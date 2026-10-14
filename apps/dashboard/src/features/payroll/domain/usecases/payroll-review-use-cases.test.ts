import { describe, expect, it, vi } from "vitest";
import {
  calculationCompany,
  calculationId,
} from "../../../../../tests/fixtures/payroll-calculations";
import {
  payrollReviewDetails,
  payrollReviewPreparation,
  reviewId,
} from "../../../../../tests/fixtures/payroll-reviews";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  toPayrollReviewDetails,
  toPayrollReviewPreparation,
} from "../../data/mappers/payroll-review-mapper";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
import { DecidePayrollReview } from "./decide-payroll-review";
import { LoadPayrollReviewAction } from "./load-payroll-review-action";
import { LoadPayrollReviews } from "./load-payroll-reviews";
import { SubmitPayrollReview } from "./submit-payroll-review";
import { WithdrawPayrollReview } from "./withdraw-payroll-review";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.calculate", "payroll.review"],
  },
  signal = new AbortController().signal,
  operation = reviewId as OperationId;
function repository() {
  return {
    prepare: vi
      .fn<PayrollReviewRepository["prepare"]>()
      .mockResolvedValue(
        success(
          toPayrollReviewPreparation(payrollReviewPreparation(), access.companyId, calculationId),
        ),
      ),
    list: vi.fn<PayrollReviewRepository["list"]>().mockResolvedValue(success([])),
    get: vi
      .fn<PayrollReviewRepository["get"]>()
      .mockResolvedValue(
        success(toPayrollReviewDetails(payrollReviewDetails(), access.companyId, reviewId)),
      ),
    submit: vi
      .fn<PayrollReviewRepository["submit"]>()
      .mockResolvedValue(success({ id: reviewId, version: 0 })),
    decide: vi
      .fn<PayrollReviewRepository["decide"]>()
      .mockResolvedValue(success({ id: reviewId, version: 1 })),
    withdraw: vi
      .fn<PayrollReviewRepository["withdraw"]>()
      .mockResolvedValue(success({ id: reviewId, version: 1 })),
  };
}
describe("payroll review policies", () => {
  it.each(["company.manage", "payroll.self.read", "payroll.policy.manage"])(
    "%s does not acquire financial review data",
    async (permission) => {
      const repo = repository();
      expect(
        await new LoadPayrollReviews(repo).execute(
          { ...access, permissions: [permission] },
          calculationId,
          signal,
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(repo.prepare).not.toHaveBeenCalled();
    },
  );
  it("stops the overview between repository reads on failure or cancellation", async () => {
    const repo = repository();
    repo.prepare.mockResolvedValueOnce(failed("access_denied"));
    await new LoadPayrollReviews(repo).execute(access, calculationId, signal);
    expect(repo.list).not.toHaveBeenCalled();
    const abort = new AbortController();
    repo.prepare.mockImplementationOnce(async () => {
      abort.abort();
      return success(
        toPayrollReviewPreparation(payrollReviewPreparation(), access.companyId, calculationId),
      );
    });
    await expect(
      new LoadPayrollReviews(repo).execute(access, calculationId, abort.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(repo.list).not.toHaveBeenCalled();
  });
  it("requires the server action hint for a new decision but not for original-command recovery", async () => {
    const repo = repository();
    expect(
      await new LoadPayrollReviewAction(repo).execute(access, reviewId, "APPROVE", signal),
    ).toMatchObject({ ok: false, failure: { code: "payroll_review_action_unavailable" } });
    expect(
      (
        await new DecidePayrollReview(repo).execute(
          access,
          reviewId,
          operation,
          { expectedVersion: 0, expectedApprovalVersion: 7, decision: "APPROVE", reason: "" },
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(repo.get).toHaveBeenCalledTimes(1);
    expect(repo.decide.mock.calls[0]?.[3]).toMatchObject({
      expectedVersion: 0,
      expectedApprovalVersion: 7,
    });
  });
  it("keeps calculation and independent decision grants separate", async () => {
    const repo = repository(),
      input = { expectedVersion: 0, expectedApprovalVersion: 0, reason: "Reviewed" };
    await new WithdrawPayrollReview(repo).execute(
      { ...access, permissions: ["payroll.review"] },
      reviewId,
      operation,
      input,
      signal,
    );
    await new DecidePayrollReview(repo).execute(
      { ...access, permissions: ["payroll.calculate"] },
      reviewId,
      operation,
      { ...input, decision: "APPROVE" },
      signal,
    );
    await new SubmitPayrollReview(repo).execute(
      { ...access, permissions: ["payroll.read"] },
      calculationId,
      operation,
      { id: reviewId, expectedRunVersion: 1, reason: "Reviewed" },
      signal,
    );
    expect(repo.withdraw).not.toHaveBeenCalled();
    expect(repo.decide).not.toHaveBeenCalled();
    expect(repo.submit).not.toHaveBeenCalled();
  });
  it("normalizes a submission once and rejects invalid versions or blank rejection reasons", async () => {
    const repo = repository();
    expect(
      (
        await new SubmitPayrollReview(repo).execute(
          access,
          calculationId.toUpperCase(),
          operation,
          { id: reviewId.toUpperCase(), expectedRunVersion: 1, reason: "  Reviewed  " },
          signal,
        )
      ).ok,
    ).toBe(true);
    expect(repo.submit.mock.calls[0]?.slice(0, 4)).toEqual([
      access.companyId,
      calculationId,
      operation,
      { id: reviewId, expectedRunVersion: 1, reason: "Reviewed" },
    ]);
    await new DecidePayrollReview(repo).execute(
      access,
      reviewId,
      operation,
      { expectedVersion: 0, expectedApprovalVersion: 0, decision: "REJECT", reason: " " },
      signal,
    );
    await new WithdrawPayrollReview(repo).execute(
      access,
      reviewId,
      operation,
      { expectedVersion: 10, expectedApprovalVersion: 0, reason: "Valid reason" },
      signal,
    );
    expect(repo.decide).not.toHaveBeenCalled();
    expect(repo.withdraw).not.toHaveBeenCalled();
    expect(repo.get).not.toHaveBeenCalled();
  });
});
