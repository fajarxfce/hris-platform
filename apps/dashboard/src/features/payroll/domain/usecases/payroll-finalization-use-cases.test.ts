import { describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  finalizationId,
  payrollFinalizationDetails,
  payrollFinalizationPreparation,
} from "../../../../../tests/fixtures/payroll-finalizations";
import { reviewId } from "../../../../../tests/fixtures/payroll-reviews";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollFinalizationDetails,
  toPayrollFinalizationPreparation,
} from "../../data/mappers/payroll-finalization-mapper";
import type { PayrollFinalizationStart } from "../entities/payroll-finalization-command";
import type { PayrollPublicationSearch } from "../entities/payroll-publication-search";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
import { ListPayrollPublicationRuns } from "./list-payroll-publication-runs";
import { LoadPayrollFinalization } from "./load-payroll-finalization";
import { LoadPayrollFinalizations } from "./load-payroll-finalizations";
import { ReviewPayrollFinalization } from "./review-payroll-finalization";
import { StartPayrollFinalization } from "./start-payroll-finalization";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.finalize"],
  },
  signal = new AbortController().signal,
  operation = finalizationId as OperationId,
  search: PayrollPublicationSearch = {
    from: "2026-09",
    until: "2026-09",
    status: null,
    after: null,
  },
  input: PayrollFinalizationStart = {
    id: finalizationId,
    reviewId,
    expectedRunVersion: 1,
    expectedReviewVersion: 2,
    expectedApprovalVersion: 3,
    reason: "Reviewed publication",
  };
function repository() {
  return {
    runs: vi
      .fn<PayrollFinalizationRepository["runs"]>()
      .mockResolvedValue(success({ items: [], nextCursor: null })),
    prepare: vi
      .fn<PayrollFinalizationRepository["prepare"]>()
      .mockResolvedValue(
        success(
          toPayrollFinalizationPreparation(
            payrollFinalizationPreparation(),
            access.companyId,
            calculationId,
          ),
        ),
      ),
    list: vi.fn<PayrollFinalizationRepository["list"]>().mockResolvedValue(success([])),
    get: vi
      .fn<PayrollFinalizationRepository["get"]>()
      .mockResolvedValue(
        success(
          toPayrollFinalizationDetails(
            payrollFinalizationDetails(),
            access.companyId,
            finalizationId,
          ),
        ),
      ),
    start: vi
      .fn<PayrollFinalizationRepository["start"]>()
      .mockResolvedValue(success({ id: finalizationId, version: 0 })),
  };
}
describe("publication policy", () => {
  it.each(["payroll.finalize", "payroll.read", "payroll.calculate", "payroll.review"])(
    "%s reads publication references through its own scope",
    async (permission) => {
      const repo = repository(),
        actor = { ...access, permissions: [permission] };
      expect((await new ListPayrollPublicationRuns(repo).execute(actor, search, signal)).ok).toBe(
        true,
      );
      expect(
        (await new LoadPayrollFinalizations(repo).execute(actor, calculationId, signal)).ok,
      ).toBe(true);
      expect(
        (await new LoadPayrollFinalization(repo).execute(actor, finalizationId, signal)).ok,
      ).toBe(true);
    },
  );
  it.each([
    "payroll.self.read",
    "company.manage",
    "payroll.policy.manage",
    "payroll.compensation.manage",
  ])("%s does not acquire company publication data", async (permission) => {
    const repo = repository(),
      actor = { ...access, permissions: [permission] };
    expect(await new ListPayrollPublicationRuns(repo).execute(actor, search, signal)).toMatchObject(
      { ok: false, failure: { code: "access_denied" } },
    );
    await new LoadPayrollFinalizations(repo).execute(actor, calculationId, signal);
    await new LoadPayrollFinalization(repo).execute(actor, finalizationId, signal);
    expect(repo.runs).not.toHaveBeenCalled();
    expect(repo.prepare).not.toHaveBeenCalled();
    expect(repo.get).not.toHaveBeenCalled();
  });
  it("requires independent finalization permission for the form and command", async () => {
    const repo = repository(),
      actor = { ...access, permissions: ["payroll.read", "payroll.calculate", "payroll.review"] };
    await new ReviewPayrollFinalization(repo).execute(actor, calculationId, signal);
    await new StartPayrollFinalization(repo).execute(
      actor,
      calculationId,
      operation,
      input,
      signal,
    );
    expect(repo.prepare).not.toHaveBeenCalled();
    expect(repo.start).not.toHaveBeenCalled();
  });
  it("bounds publication searches and refuses malformed positions before I/O", async () => {
    const repo = repository(),
      action = new ListPayrollPublicationRuns(repo);
    for (const invalid of [
      { ...search, from: "2023-01" },
      { ...search, until: "2026-08" },
      { ...search, from: "2024-01", until: "2027-01" },
      { ...search, after: "bad" },
    ])
      expect(await action.execute(access, invalid, signal)).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    expect(repo.runs).not.toHaveBeenCalled();
    expect(
      (await action.execute(access, { ...search, from: "2024-01", until: "2026-12" }, signal)).ok,
    ).toBe(true);
  });
  it("keeps observed versions and the original reason without a preflight read during recovery", async () => {
    const repo = repository(),
      original = {
        ...input,
        id: input.id.toUpperCase(),
        reviewId: input.reviewId.toUpperCase(),
        reason: "  Reviewed payroll  ",
      };
    expect(
      (
        await new StartPayrollFinalization(repo).execute(
          access,
          calculationId.toUpperCase(),
          operation,
          original,
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(repo.start.mock.calls[0]?.slice(0, 4)).toEqual([
      access.companyId,
      calculationId,
      operation,
      { ...input, reason: original.reason },
    ]);
    expect(repo.prepare).not.toHaveBeenCalled();
    expect(Object.isFrozen(repo.start.mock.calls[0]?.[3])).toBe(true);
  });
  it("validates identifiers, every approval version and the required reason", async () => {
    const repo = repository(),
      action = new StartPayrollFinalization(repo);
    for (const change of [
      { id: "bad" },
      { reviewId: "bad" },
      { expectedRunVersion: 24 },
      { expectedReviewVersion: 0 },
      { expectedReviewVersion: 9 },
      { expectedApprovalVersion: 0 },
      { expectedApprovalVersion: Number.MAX_SAFE_INTEGER + 1 },
      { reason: " " },
      { reason: "x".repeat(1001) },
    ])
      expect(
        await action.execute(access, calculationId, operation, { ...input, ...change }, signal),
      ).toMatchObject({ ok: false, failure: { code: "invalid_payroll_finalization" } });
    expect(repo.start).not.toHaveBeenCalled();
  });
  it("preserves acquisition failure and stops overview acquisition on cancellation", async () => {
    const repo = repository(),
      action = new LoadPayrollFinalizations(repo);
    repo.prepare.mockResolvedValueOnce(failed("access_denied"));
    expect(await action.execute(access, calculationId, signal)).toEqual(failed("access_denied"));
    expect(repo.list).not.toHaveBeenCalled();
    const abort = new AbortController();
    repo.prepare.mockImplementationOnce(async () => {
      abort.abort();
      return success(
        toPayrollFinalizationPreparation(
          payrollFinalizationPreparation(),
          access.companyId,
          calculationId,
        ),
      );
    });
    await expect(action.execute(access, calculationId, abort.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    expect(repo.list).not.toHaveBeenCalled();
  });
});
