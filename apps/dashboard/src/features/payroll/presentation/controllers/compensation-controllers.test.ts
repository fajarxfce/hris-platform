import { assert, describe, expect, it, vi } from "vitest";
import {
  compensationCompany,
  compensationEmployee,
  compensationPage,
  compensationRecord,
  compensationTerms,
} from "../../../../../tests/fixtures/compensations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toCompensationReview } from "../../data/mappers/compensation-mapper";
import type { CompensationReview } from "../../domain/entities/compensation-review";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { CompensationController } from "./compensation-controller";
import { CompensationEditorController } from "./compensation-editor-controller";
import { CompensationEmployeesController } from "./compensation-employees-controller";

const access: CompanyAccess = {
  companyId: compensationCompany as CompanyId,
  permissions: ["payroll.compensation.manage"],
};
const review = (version = 23) =>
  toCompensationReview(
    compensationPage(compensationRecord(version)),
    access.companyId,
    compensationEmployee,
    "2026-06",
    null,
  );
const fields = () => ({
  effectiveFrom: "2026-06",
  terms: compensationTerms(),
  reason: "Reviewed employee compensation",
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadCompensationReview: {
      execute: vi
        .fn<PayrollUseCases["loadCompensationReview"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    listCompensationEmployees: {
      execute: vi
        .fn<PayrollUseCases["listCompensationEmployees"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { items: [review().employee], nextCursor: null } }),
    },
    loadCompensationEditor: {
      execute: vi.fn<PayrollUseCases["loadCompensationEditor"]["execute"]>().mockResolvedValue({
        ok: true,
        value: { employee: review().employee, current: review().current },
      }),
    },
    saveCompensation: {
      execute: vi
        .fn<PayrollUseCases["saveCompensation"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: compensationEmployee, version: 24 } }),
    },
  };
}
function editor(useCases: ReturnType<typeof actions>) {
  let next = 0;
  return new CompensationEditorController(
    useCases,
    access,
    compensationEmployee,
    "2026-06",
    () => `ca200000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
  );
}
describe("compensation presentation ownership", () => {
  it("inspects immutable revisions without fetching and replaces pending review safely", async () => {
    const useCases = actions(),
      controller = new CompensationController(
        useCases.loadCompensationReview,
        access,
        compensationEmployee,
        "2026-06",
        null,
      );
    controller.activate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    controller.selectRevision("0");
    expect(controller.getSnapshot().selected?.compensation.appliedRevision).toBe(0);
    controller.inspectConfiguration();
    expect(controller.getSnapshot().selected?.compensation.appliedRevision).toBe(23);
    expect(useCases.loadCompensationReview.execute).toHaveBeenCalledTimes(1);
    const old = deferred<Result<CompensationReview>>();
    useCases.loadCompensationReview.execute
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ ok: true, value: review(24) });
    const pending = controller.refresh();
    const signal = useCases.loadCompensationReview.execute.mock.calls.at(-1)?.[4];
    await controller.refresh();
    expect(signal?.aborted).toBe(true);
    old.resolve({ ok: true, value: review() });
    await pending;
    expect(controller.getSnapshot().review?.current?.version).toBe(24);
    expect(controller.getSnapshot().selected).toBeNull();
    controller.deactivate();
  });
  it("stops directory observation and ignores late failure after disposal", async () => {
    const useCases = actions(),
      gate =
        deferred<Awaited<ReturnType<PayrollUseCases["listCompensationEmployees"]["execute"]>>>();
    useCases.listCompensationEmployees.execute.mockReturnValueOnce(gate.promise);
    const controller = new CompensationEmployeesController(
      useCases.listCompensationEmployees,
      access,
      "Rani",
      null,
    );
    const listener = vi.fn(),
      unsubscribe = controller.subscribe(listener);
    controller.activate();
    const signal = useCases.listCompensationEmployees.execute.mock.calls[0]?.[3];
    controller.deactivate();
    const count = listener.mock.calls.length;
    gate.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(signal?.aborted).toBe(true);
    expect(controller.getSnapshot().failure).toBeNull();
    expect(listener).toHaveBeenCalledTimes(count);
    unsubscribe();
  });
  it("freezes nested contents, salary/employment observations and operation through a lost save", async () => {
    const useCases = actions(),
      gate = deferred<Result<MutationReceipt>>(),
      controller = editor(useCases);
    useCases.saveCompensation.execute
      .mockReturnValueOnce(gate.promise)
      .mockResolvedValueOnce(failed("stale_employment_version"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const input = fields(),
      pending = controller.save(input);
    input.terms.tax.verificationReference = "Changed after save";
    input.terms.fixedEarnings.length = 0;
    await controller.save(input);
    expect(useCases.saveCompensation.execute).toHaveBeenCalledTimes(1);
    gate.resolve(failed("connection_unavailable"));
    await pending;
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    expect(useCases.loadCompensationEditor.execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    const original = useCases.saveCompensation.execute.mock.calls[0];
    assert(original);
    expect(original[3]).toMatchObject({
      expectedVersion: 23,
      expectedEmploymentVersion: 7,
      terms: {
        fixedEarnings: [{ code: "MEAL" }],
        tax: { verificationReference: "Verified fictional employee registration" },
      },
    });
    for (const call of useCases.saveCompensation.execute.mock.calls)
      expect(call.slice(0, 4)).toEqual(original.slice(0, 4));
    controller.deactivate();
  });
  it.each(["stale_version", "stale_employment_version", "payroll_period_frozen"])(
    "requires new review after definite %s",
    async (code) => {
      const useCases = actions(),
        controller = editor(useCases);
      useCases.saveCompensation.execute.mockResolvedValueOnce(failed(code));
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      await controller.save(fields());
      expect(controller.getSnapshot().stage).toBe("conflict");
      await controller.save(fields());
      expect(useCases.saveCompensation.execute).toHaveBeenCalledTimes(1);
      useCases.loadCompensationEditor.execute.mockResolvedValue({
        ok: true,
        value: {
          employee: { ...review().employee, employmentVersion: 8 },
          current: review(24).current,
        },
      });
      await controller.refresh();
      await controller.save(fields());
      expect(
        useCases.saveCompensation.execute.mock.calls.map((call) => [
          call[3].expectedVersion,
          call[3].expectedEmploymentVersion,
        ]),
      ).toEqual([
        [23, 7],
        [24, 8],
      ]);
      expect(useCases.saveCompensation.execute.mock.calls[0]?.[2]).not.toBe(
        useCases.saveCompensation.execute.mock.calls[1]?.[2],
      );
      controller.deactivate();
    },
  );
  it("cannot turn a late self-change rejection into an editable unknown command", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveCompensation.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("self_compensation_change_denied"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields());
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", context: null });
    await controller.save(fields());
    await controller.refresh();
    expect(useCases.saveCompensation.execute).toHaveBeenCalledTimes(2);
    expect(useCases.loadCompensationEditor.execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });
  it("keeps the form after assurance rejection, but drops private data for definite scope loss", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveCompensation.execute
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("access_denied"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const context = controller.getSnapshot().context;
    await controller.save(fields());
    expect(controller.getSnapshot()).toMatchObject({ stage: "editing", context });
    await controller.save(fields());
    expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", context: null });
    controller.deactivate();
  });
  it("aborts pending saves on disposal and never releases the late receipt", async () => {
    const useCases = actions(),
      controller = editor(useCases),
      gate = deferred<Result<MutationReceipt>>();
    useCases.saveCompensation.execute.mockReturnValueOnce(gate.promise);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const pending = controller.save(fields()),
      signal = useCases.saveCompensation.execute.mock.calls[0]?.[4];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    gate.resolve({ ok: true, value: { id: compensationEmployee, version: 24 } });
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().context).toBeNull();
  });
});
