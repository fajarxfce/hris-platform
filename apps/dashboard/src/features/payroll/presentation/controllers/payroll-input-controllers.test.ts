import { assert, describe, expect, it, vi } from "vitest";
import {
  inputAdjustment,
  inputCompany,
  inputEmployee,
  inputId,
  inputMonth,
  inputRecord,
  inputReview,
  inputTerms,
} from "../../../../../tests/fixtures/payroll-inputs";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollInput, toPayrollInputReview } from "../../data/mappers/payroll-input-mapper";
import type { PayrollInput } from "../../domain/entities/payroll-input";
import type { PayrollInputReview } from "../../domain/entities/payroll-input-review";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { PayrollInputController } from "./payroll-input-controller";
import { PayrollInputEditorController } from "./payroll-input-editor-controller";
import { PayrollInputEmployeesController } from "./payroll-input-employees-controller";
import { PayrollInputVerificationController } from "./payroll-input-verification-controller";

const access: CompanyAccess = {
  companyId: inputCompany as CompanyId,
  permissions: ["payroll.calculate", "payroll.review"],
};
const review = (version = 23) =>
  toPayrollInputReview(
    inputReview(inputRecord(version)),
    access.companyId,
    inputEmployee,
    inputMonth,
    null,
  );
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
function actions() {
  return {
    loadInputReview: {
      execute: vi
        .fn<PayrollUseCases["loadInputReview"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    loadInputRevision: {
      execute: vi.fn<PayrollUseCases["loadInputRevision"]["execute"]>().mockResolvedValue({
        ok: true,
        value: toPayrollInput(inputRecord(0), access.companyId, inputEmployee, inputMonth),
      }),
    },
    reviewInputAction: {
      execute: vi
        .fn<PayrollUseCases["reviewInputAction"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    listPayrollInputEmployees: {
      execute: vi
        .fn<PayrollUseCases["listPayrollInputEmployees"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { items: [], nextCursor: null } }),
    },
    saveInput: {
      execute: vi
        .fn<PayrollUseCases["saveInput"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: inputId, version: 24 } }),
    },
    verifyInput: {
      execute: vi
        .fn<PayrollUseCases["verifyInput"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: inputId, version: 24 } }),
    },
  };
}
function editor(useCases: ReturnType<typeof actions>) {
  let next = 0;
  return new PayrollInputEditorController(
    useCases,
    access,
    inputEmployee,
    inputMonth,
    () => `ba900000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
  );
}
const fields = () => ({ terms: inputTerms(), reason: "Reviewed monthly input" });
describe("monthly input presentation ownership", () => {
  it("cancels replaced reviews and ignores late failures after refresh", async () => {
    const useCases = actions(),
      old = deferred<Result<PayrollInputReview>>();
    useCases.loadInputReview.execute.mockReturnValueOnce(old.promise);
    const controller = new PayrollInputController(
      useCases,
      access,
      inputEmployee,
      inputMonth,
      null,
    );
    controller.activate();
    controller.activate();
    const first = useCases.loadInputReview.execute.mock.calls[0]?.[4];
    await controller.refresh();
    expect(first?.aborted).toBe(true);
    old.reject(new Error("Late acquisition failure"));
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    expect(controller.getSnapshot().failure).toBeNull();
    controller.deactivate();
    expect(controller.getSnapshot().review).toBeNull();
  });
  it("owns pending revision selection and clears private evidence on denied reads", async () => {
    const useCases = actions(),
      controller = new PayrollInputController(useCases, access, inputEmployee, inputMonth, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const selected = deferred<Result<PayrollInput>>();
    useCases.loadInputRevision.execute.mockReturnValueOnce(selected.promise);
    const pending = controller.selectRevision("0");
    expect(controller.getSnapshot().inspecting).toBe(true);
    controller.closeInspection();
    expect(useCases.loadInputRevision.execute.mock.calls[0]?.[4].aborted).toBe(true);
    selected.resolve({
      ok: true,
      value: toPayrollInput(inputRecord(0), access.companyId, inputEmployee, inputMonth),
    });
    await pending;
    expect(controller.getSnapshot().selected).toBeNull();
    useCases.loadInputRevision.execute.mockResolvedValueOnce(failed("access_denied"));
    await controller.selectRevision("1");
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      selected: null,
    });
    controller.deactivate();
  });
  it("does not fetch revisions outside the bounded acquired history", async () => {
    const useCases = actions(),
      controller = new PayrollInputController(useCases, access, inputEmployee, inputMonth, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.selectRevision("999");
    expect(useCases.loadInputRevision.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
  it("keeps one immutable command through response loss and a later rejected retry", async () => {
    const useCases = actions(),
      controller = editor(useCases),
      input = fields(),
      pending = deferred<Result<MutationReceipt>>();
    useCases.saveInput.execute
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(failed("access_denied"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const saving = controller.save(input);
    await controller.save(fields());
    expect(useCases.saveInput.execute).toHaveBeenCalledTimes(1);
    input.terms.earnings.length = 0;
    pending.resolve(failed("connection_unavailable"));
    await saving;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    const original = useCases.saveInput.execute.mock.calls[0];
    assert(original);
    expect(original[4]).toMatchObject({
      expectedVersion: 23,
      expectedEmploymentVersion: 7,
      expectedWorkPeriodVersion: 2,
    });
    expect(original[4].terms.earnings).toHaveLength(1);
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", context: null });
    await controller.refresh();
    expect(useCases.reviewInputAction.execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of useCases.saveInput.execute.mock.calls)
      expect(call.slice(0, 5)).toEqual(original.slice(0, 5));
    controller.deactivate();
  });
  it("requires a new review after a definite source conflict and pins its new versions", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveInput.execute.mockResolvedValueOnce(failed("stale_workforce_version"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields());
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(fields());
    expect(useCases.saveInput.execute).toHaveBeenCalledTimes(1);
    const next = review(24);
    assert(next.source);
    useCases.reviewInputAction.execute.mockResolvedValueOnce({
      ok: true,
      value: {
        ...next,
        employee: { ...next.employee, employmentVersion: 8 },
        source: { ...next.source, version: 3, adjustment: inputAdjustment() },
      },
    });
    await controller.refresh();
    await controller.save(fields());
    expect(useCases.saveInput.execute.mock.calls[1]?.[4]).toMatchObject({
      expectedVersion: 24,
      expectedEmploymentVersion: 8,
      expectedWorkPeriodVersion: 3,
      workAdjustmentId: inputAdjustment().id,
    });
    expect(useCases.saveInput.execute.mock.calls[1]?.[3]).not.toBe(
      useCases.saveInput.execute.mock.calls[0]?.[3],
    );
    controller.deactivate();
  });
  it("allows correction after rejected validation without retaining a failed command", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveInput.execute.mockResolvedValueOnce(failed("invalid_payroll_input"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields());
    expect(controller.getSnapshot().stage).toBe("editing");
    await controller.save(fields());
    expect(useCases.saveInput.execute.mock.calls[1]?.[3]).not.toBe(
      useCases.saveInput.execute.mock.calls[0]?.[3],
    );
    controller.deactivate();
  });
  it.each([true, false])(
    "disposes a pending save and ignores late completion (success=%s)",
    async (success) => {
      const useCases = actions(),
        controller = editor(useCases),
        old = deferred<Result<MutationReceipt>>();
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      useCases.saveInput.execute.mockReturnValueOnce(old.promise);
      const saving = controller.save(fields());
      const signal = useCases.saveInput.execute.mock.calls[0]?.[5];
      controller.deactivate();
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      expect(signal?.aborted).toBe(true);
      if (success) old.resolve({ ok: true, value: { id: inputId, version: 24 } });
      else old.reject(new Error("Late failure"));
      await saving;
      expect(controller.getSnapshot()).toMatchObject({
        stage: "editing",
        receipt: null,
        failure: null,
      });
      controller.deactivate();
    },
  );
  it("verifies the observed version and retries the original review after response loss", async () => {
    const useCases = actions(),
      controller = new PayrollInputVerificationController(
        useCases,
        access,
        inputEmployee,
        inputMonth,
        () => "ba900000-0000-4000-8000-000000000001",
      );
    useCases.verifyInput.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("payroll_input_already_verified"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    expect(useCases.reviewInputAction.execute.mock.calls[0]?.[3]).toBe("VERIFY");
    await controller.verify(" Independent review ");
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.verify("Changed reason");
    await controller.retryVerification();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retryVerification();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of useCases.verifyInput.execute.mock.calls)
      expect(call[4]).toEqual({ expectedVersion: 23, reason: "Independent review" });
    expect(useCases.saveInput.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
  it("disposes paginated discovery and prevents an old account result from replacing a new owner", async () => {
    const useCases = actions(),
      old =
        deferred<Awaited<ReturnType<PayrollUseCases["listPayrollInputEmployees"]["execute"]>>>();
    useCases.listPayrollInputEmployees.execute.mockReturnValueOnce(old.promise);
    const controller = new PayrollInputEmployeesController(
      useCases.listPayrollInputEmployees,
      access,
      { month: inputMonth, query: "", status: null, after: null },
    );
    controller.activate();
    controller.deactivate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(failed("company_access_denied"));
    await Promise.resolve();
    expect(controller.getSnapshot().failure).toBeNull();
    expect(useCases.listPayrollInputEmployees.execute.mock.calls[0]?.[2].aborted).toBe(true);
    controller.deactivate();
  });
});
