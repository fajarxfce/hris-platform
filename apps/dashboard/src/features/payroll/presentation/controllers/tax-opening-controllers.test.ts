import { assert, describe, expect, it, vi } from "vitest";
import {
  openingCompany,
  openingEmployee,
  openingId,
  openingRecord,
  openingReview,
  openingTerms,
  openingYear,
} from "../../../../../tests/fixtures/tax-openings";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toTaxOpening, toTaxOpeningReview } from "../../data/mappers/tax-opening-mapper";
import type { TaxOpening } from "../../domain/entities/tax-opening";
import type { TaxOpeningReview } from "../../domain/entities/tax-opening-review";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { TaxOpeningController } from "./tax-opening-controller";
import { TaxOpeningEditorController } from "./tax-opening-editor-controller";
import { TaxOpeningEmployeesController } from "./tax-opening-employees-controller";
import { TaxOpeningVerificationController } from "./tax-opening-verification-controller";

const access: CompanyAccess = {
  companyId: openingCompany as CompanyId,
  permissions: ["payroll.calculate", "payroll.review"],
};
const review = (version = 23) =>
  toTaxOpeningReview(
    openingReview(openingRecord(version)),
    access.companyId,
    openingEmployee,
    openingYear,
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
    loadTaxOpeningReview: {
      execute: vi
        .fn<PayrollUseCases["loadTaxOpeningReview"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    loadTaxOpeningRevision: {
      execute: vi.fn<PayrollUseCases["loadTaxOpeningRevision"]["execute"]>().mockResolvedValue({
        ok: true,
        value: toTaxOpening(openingRecord(0), access.companyId, openingEmployee, openingYear),
      }),
    },
    reviewTaxOpeningAction: {
      execute: vi
        .fn<PayrollUseCases["reviewTaxOpeningAction"]["execute"]>()
        .mockResolvedValue({ ok: true, value: review() }),
    },
    listTaxOpeningEmployees: {
      execute: vi
        .fn<PayrollUseCases["listTaxOpeningEmployees"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { items: [], nextCursor: null } }),
    },
    saveTaxOpening: {
      execute: vi
        .fn<PayrollUseCases["saveTaxOpening"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: openingId, version: 24 } }),
    },
    verifyTaxOpening: {
      execute: vi
        .fn<PayrollUseCases["verifyTaxOpening"]["execute"]>()
        .mockResolvedValue({ ok: true, value: { id: openingId, version: 24 } }),
    },
  };
}
function editor(useCases: ReturnType<typeof actions>) {
  let next = 0;
  return new TaxOpeningEditorController(
    useCases,
    access,
    openingEmployee,
    openingYear,
    () => `ba900000-0000-4000-8000-${String(++next).padStart(12, "0")}`,
  );
}
const fields = () => ({ terms: openingTerms(), reason: "Reviewed monthly input" });
describe("monthly input presentation ownership", () => {
  it("cancels replaced reviews and ignores late failures after refresh", async () => {
    const useCases = actions(),
      old = deferred<Result<TaxOpeningReview>>();
    useCases.loadTaxOpeningReview.execute.mockReturnValueOnce(old.promise);
    const controller = new TaxOpeningController(
      useCases,
      access,
      openingEmployee,
      openingYear,
      null,
    );
    controller.activate();
    controller.activate();
    const first = useCases.loadTaxOpeningReview.execute.mock.calls[0]?.[4];
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
      controller = new TaxOpeningController(useCases, access, openingEmployee, openingYear, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const selected = deferred<Result<TaxOpening>>();
    useCases.loadTaxOpeningRevision.execute.mockReturnValueOnce(selected.promise);
    const pending = controller.selectRevision("0");
    expect(controller.getSnapshot().inspecting).toBe(true);
    controller.closeInspection();
    expect(useCases.loadTaxOpeningRevision.execute.mock.calls[0]?.[4].aborted).toBe(true);
    selected.resolve({
      ok: true,
      value: toTaxOpening(openingRecord(0), access.companyId, openingEmployee, openingYear),
    });
    await pending;
    expect(controller.getSnapshot().selected).toBeNull();
    useCases.loadTaxOpeningRevision.execute.mockResolvedValueOnce(failed("access_denied"));
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
      controller = new TaxOpeningController(useCases, access, openingEmployee, openingYear, null);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.selectRevision("999");
    expect(useCases.loadTaxOpeningRevision.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
  it("keeps one immutable command through response loss and a later rejected retry", async () => {
    const useCases = actions(),
      controller = editor(useCases),
      input = fields(),
      pending = deferred<Result<MutationReceipt>>();
    useCases.saveTaxOpening.execute
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(failed("access_denied"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const saving = controller.save(input);
    await controller.save(fields());
    expect(useCases.saveTaxOpening.execute).toHaveBeenCalledTimes(1);
    input.terms.history.taxableGross = "0";
    pending.resolve(failed("connection_unavailable"));
    await saving;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    const original = useCases.saveTaxOpening.execute.mock.calls[0];
    assert(original);
    expect(original[4]).toMatchObject({
      expectedVersion: 23,
      expectedEmploymentVersion: 7,
    });
    expect(original[4].terms.history.taxableGross).toBe("60000000.25");
    await controller.retrySave();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", context: null });
    await controller.refresh();
    expect(useCases.reviewTaxOpeningAction.execute).toHaveBeenCalledTimes(1);
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of useCases.saveTaxOpening.execute.mock.calls)
      expect(call.slice(0, 5)).toEqual(original.slice(0, 5));
    controller.deactivate();
  });
  it("requires a new review after a definite employment conflict and pins its new versions", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveTaxOpening.execute.mockResolvedValueOnce(failed("stale_employment_version"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields());
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save(fields());
    expect(useCases.saveTaxOpening.execute).toHaveBeenCalledTimes(1);
    const next = review(24);
    useCases.reviewTaxOpeningAction.execute.mockResolvedValueOnce({
      ok: true,
      value: {
        ...next,
        employee: { ...next.employee, employmentVersion: 8 },
      },
    });
    await controller.refresh();
    await controller.save(fields());
    expect(useCases.saveTaxOpening.execute.mock.calls[1]?.[4]).toMatchObject({
      expectedVersion: 24,
      expectedEmploymentVersion: 8,
    });
    expect(useCases.saveTaxOpening.execute.mock.calls[1]?.[3]).not.toBe(
      useCases.saveTaxOpening.execute.mock.calls[0]?.[3],
    );
    controller.deactivate();
  });
  it("allows correction after rejected validation without retaining a failed command", async () => {
    const useCases = actions(),
      controller = editor(useCases);
    useCases.saveTaxOpening.execute.mockResolvedValueOnce(failed("invalid_payroll_tax_opening"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save(fields());
    expect(controller.getSnapshot().stage).toBe("editing");
    await controller.save(fields());
    expect(useCases.saveTaxOpening.execute.mock.calls[1]?.[3]).not.toBe(
      useCases.saveTaxOpening.execute.mock.calls[0]?.[3],
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
      useCases.saveTaxOpening.execute.mockReturnValueOnce(old.promise);
      const saving = controller.save(fields());
      const signal = useCases.saveTaxOpening.execute.mock.calls[0]?.[5];
      controller.deactivate();
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      expect(signal?.aborted).toBe(true);
      if (success) old.resolve({ ok: true, value: { id: openingId, version: 24 } });
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
      controller = new TaxOpeningVerificationController(
        useCases,
        access,
        openingEmployee,
        openingYear,
        () => "ba900000-0000-4000-8000-000000000001",
      );
    useCases.verifyTaxOpening.execute
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("payroll_tax_opening_already_verified"));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    expect(useCases.reviewTaxOpeningAction.execute.mock.calls[0]?.[3]).toBe("VERIFY");
    await controller.verify(" Independent review ");
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.verify("Changed reason");
    await controller.retryVerification();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.retryVerification();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of useCases.verifyTaxOpening.execute.mock.calls)
      expect(call[4]).toEqual({ expectedVersion: 23, reason: "Independent review" });
    expect(useCases.saveTaxOpening.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
  it("disposes paginated discovery and prevents an old account result from replacing a new owner", async () => {
    const useCases = actions(),
      old = deferred<Awaited<ReturnType<PayrollUseCases["listTaxOpeningEmployees"]["execute"]>>>();
    useCases.listTaxOpeningEmployees.execute.mockReturnValueOnce(old.promise);
    const controller = new TaxOpeningEmployeesController(useCases.listTaxOpeningEmployees, access, {
      year: openingYear,
      query: "",
      status: null,
      after: null,
    });
    controller.activate();
    controller.deactivate();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    old.resolve(failed("company_access_denied"));
    await Promise.resolve();
    expect(controller.getSnapshot().failure).toBeNull();
    expect(useCases.listTaxOpeningEmployees.execute.mock.calls[0]?.[2].aborted).toBe(true);
    controller.deactivate();
  });
});
