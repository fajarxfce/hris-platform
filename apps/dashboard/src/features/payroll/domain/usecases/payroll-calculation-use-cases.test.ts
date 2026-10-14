import { assert, describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationDetails,
  calculationEmployeeId,
  calculationId,
  calculationPeriod,
  calculationReview,
  calculationRun,
  calculationStart,
} from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollCalculationReview,
  toPayrollRunDetails,
} from "../../data/mappers/payroll-calculation-mapper";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
import { AbandonPayrollCalculation } from "./abandon-payroll-calculation";
import { LoadPayrollCalculations } from "./load-payroll-calculations";
import { LoadPayrollRun } from "./load-payroll-run";
import { LoadPayrollRunEmployee } from "./load-payroll-run-employee";
import { ResumePayrollCalculation } from "./resume-payroll-calculation";
import { ReviewPayrollCalculation } from "./review-payroll-calculation";
import { ReviewPayrollRunRecovery } from "./review-payroll-run-recovery";
import { StartPayrollCalculation } from "./start-payroll-calculation";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.calculate"],
  },
  signal = new AbortController().signal,
  operation = calculationId as OperationId;
function repository() {
  const review = toPayrollCalculationReview(
      calculationReview(),
      access.companyId,
      calculationPeriod,
    ),
    run = toPayrollRunDetails(calculationDetails(), access.companyId, calculationId, null, 50);
  return {
    review: vi.fn<PayrollCalculationRepository["review"]>().mockResolvedValue(success(review)),
    runs: vi.fn<PayrollCalculationRepository["runs"]>().mockResolvedValue(success([run.run])),
    run: vi.fn<PayrollCalculationRepository["run"]>().mockResolvedValue(success(run)),
    employee: vi.fn<PayrollCalculationRepository["employee"]>(),
    start: vi
      .fn<PayrollCalculationRepository["start"]>()
      .mockResolvedValue(success({ id: calculationId, version: 0 })),
    resume: vi
      .fn<PayrollCalculationRepository["resume"]>()
      .mockResolvedValue(success({ id: calculationId, version: 2 })),
    abandon: vi
      .fn<PayrollCalculationRepository["abandon"]>()
      .mockResolvedValue(success({ id: calculationId, version: 2 })),
  } satisfies PayrollCalculationRepository;
}
describe("calculation application policy", () => {
  it("denies company administration and self-service reads before any acquisition", async () => {
    const repo = repository(),
      denied = { ...access, permissions: ["company.manage", "payroll.self.read"] };
    expect(
      await new LoadPayrollCalculations(repo).execute(denied, calculationPeriod, signal),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await new LoadPayrollRun(repo).execute(denied, calculationId, null, signal),
    ).toMatchObject({ ok: false });
    expect(
      await new LoadPayrollRunEmployee(repo).execute(
        denied,
        calculationId,
        calculationEmployeeId(),
        signal,
      ),
    ).toMatchObject({ ok: false });
    expect(repo.review).not.toHaveBeenCalled();
    expect(repo.run).not.toHaveBeenCalled();
    expect(repo.employee).not.toHaveBeenCalled();
  });
  it("permits review reads without granting mutation policy", async () => {
    const repo = repository(),
      reader = { ...access, permissions: ["payroll.review"] };
    expect(
      (await new LoadPayrollCalculations(repo).execute(reader, calculationPeriod, signal)).ok,
    ).toBe(true);
    expect(
      await new ReviewPayrollCalculation(repo).execute(reader, calculationPeriod, signal),
    ).toMatchObject({ ok: false });
    expect(
      await new StartPayrollCalculation(repo).execute(
        reader,
        calculationPeriod,
        operation,
        calculationStart(),
        signal,
      ),
    ).toMatchObject({ ok: false });
    expect(repo.start).not.toHaveBeenCalled();
  });
  it("does not acquire a second source after failure or cancellation", async () => {
    const repo = repository();
    repo.review.mockResolvedValueOnce(failed("mfa_required"));
    expect(
      await new LoadPayrollCalculations(repo).execute(access, calculationPeriod, signal),
    ).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(repo.runs).not.toHaveBeenCalled();
    const abort = new AbortController(),
      review = toPayrollCalculationReview(calculationReview(), access.companyId, calculationPeriod);
    repo.review.mockImplementationOnce(async () => {
      abort.abort();
      return success(review);
    });
    await expect(
      new LoadPayrollCalculations(repo).execute(access, calculationPeriod, abort.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(repo.runs).not.toHaveBeenCalled();
  });
  it("rejects invalid resource IDs and pagination before repository calls", async () => {
    const repo = repository();
    for (const after of ["0", "-1", "5001", "01", "1e3"]) {
      expect(
        await new LoadPayrollRun(repo).execute(access, calculationId, after, signal),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    }
    expect(
      await new LoadPayrollRunEmployee(repo).execute(
        access,
        "../other",
        calculationEmployeeId(),
        signal,
      ),
    ).toMatchObject({ ok: false });
    expect(repo.run).not.toHaveBeenCalled();
    expect(repo.employee).not.toHaveBeenCalled();
  });
  it("requires a current recovery hint and captures only one employee summary", async () => {
    const repo = repository();
    expect(
      await new ReviewPayrollRunRecovery(repo).execute(access, calculationId, "resume", signal),
    ).toMatchObject({ ok: false, failure: { code: "payroll_calculation_action_unavailable" } });
    const stopped = toPayrollRunDetails(
      calculationDetails(calculationRun({ status: "STOPPED", processed: 0, succeeded: 0 })),
      access.companyId,
      calculationId,
      null,
      1,
    );
    repo.run.mockResolvedValueOnce(success(stopped));
    const result = await new ReviewPayrollRunRecovery(repo).execute(
      access,
      calculationId,
      "resume",
      signal,
    );
    assert(result.ok);
    expect(result.value.run.version).toBe(1);
    expect(repo.run.mock.calls.at(-1)).toEqual([access.companyId, calculationId, null, 1, signal]);
  });
  it("rejects unsafe values and preserves original versions and IDs through retry", async () => {
    const repo = repository(),
      action = new StartPayrollCalculation(repo);
    expect(
      await action.execute(
        access,
        calculationPeriod,
        operation,
        { ...calculationStart(), incomeDueDate: "2026-02-30" },
        signal,
      ),
    ).toMatchObject({ ok: false });
    expect(repo.start).not.toHaveBeenCalled();
    for (let i = 0; i < 2; i++)
      await action.execute(
        access,
        calculationPeriod,
        operation,
        { ...calculationStart(), reason: " Reviewed " },
        signal,
      );
    expect(repo.start.mock.calls[1]).toEqual(repo.start.mock.calls[0]);
    expect(repo.start.mock.calls[0]?.[3]).toMatchObject({
      id: calculationId,
      expectedWorkPeriodVersion: 2,
      expectedPolicyVersion: 0,
      reason: "Reviewed",
    });
    expect(repo.review).not.toHaveBeenCalled();
  });
  it("submits explicit resume and abandon versions without reacquiring or automatically retrying", async () => {
    const repo = repository(),
      input = { expectedVersion: 1, expectedPeriodVersion: 2, reason: "Correct input" };
    await new ResumePayrollCalculation(repo).execute(
      access,
      calculationId,
      operation,
      input,
      signal,
    );
    await new AbandonPayrollCalculation(repo).execute(
      access,
      calculationId,
      operation,
      input,
      signal,
    );
    expect(repo.resume).toHaveBeenCalledExactlyOnceWith(
      access.companyId,
      calculationId,
      operation,
      input,
      signal,
    );
    expect(repo.abandon).toHaveBeenCalledExactlyOnceWith(
      access.companyId,
      calculationId,
      operation,
      input,
      signal,
    );
    expect(repo.run).not.toHaveBeenCalled();
  });
});
