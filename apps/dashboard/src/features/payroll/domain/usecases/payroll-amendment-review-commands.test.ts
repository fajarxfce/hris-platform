import { describe, expect, it, vi } from "vitest";
import { amendmentReviewId } from "../../../../../tests/fixtures/payroll-amendment-reviews";
import {
  amendmentCalculationId,
  amendmentId,
} from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";
import { DecidePayrollAmendmentReview } from "./decide-payroll-amendment-review";
import { SubmitPayrollAmendmentReview } from "./submit-payroll-amendment-review";
import { WithdrawPayrollAmendmentReview } from "./withdraw-payroll-amendment-review";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend"],
  },
  operation = amendmentId(50) as OperationId,
  signal = new AbortController().signal;
function fixture() {
  return {
    get: vi.fn<PayrollAmendmentReviewRepository["get"]>(),
    list: vi.fn<PayrollAmendmentReviewRepository["list"]>(),
    submit: vi
      .fn<PayrollAmendmentReviewRepository["submit"]>()
      .mockResolvedValue(success({ id: amendmentId(), version: 2 })),
    decide: vi
      .fn<PayrollAmendmentReviewRepository["decide"]>()
      .mockResolvedValue(success({ id: amendmentId(), version: 3 })),
    withdraw: vi
      .fn<PayrollAmendmentReviewRepository["withdraw"]>()
      .mockResolvedValue(success({ id: amendmentId(), version: 3 })),
  };
}
describe("amendment review original commands", () => {
  it("submits the immutable selected calculation and keeps review identity distinct from its receipt", async () => {
    const repo = fixture(),
      command = new SubmitPayrollAmendmentReview(repo),
      input = {
        expectedVersion: 1,
        reviewId: amendmentReviewId().toUpperCase(),
        calculationId: amendmentCalculationId.toUpperCase(),
        reason: "  Independent review  ",
      };
    const result = await command.execute(
      access,
      amendmentId().toUpperCase(),
      operation,
      input,
      signal,
    );
    expect(result).toEqual(success({ id: amendmentId(), version: 2 }));
    await command.execute(access, amendmentId(), operation, input, signal);
    expect(repo.get).not.toHaveBeenCalled();
    expect(repo.list).not.toHaveBeenCalled();
    expect(repo.submit).toHaveBeenCalledTimes(2);
    expect(repo.submit.mock.calls[0]?.slice(0, 4)).toEqual([
      access.companyId,
      amendmentId(),
      operation,
      {
        expectedVersion: 1,
        reviewId: amendmentReviewId(),
        calculationId: amendmentCalculationId,
        reason: "Independent review",
      },
    ]);
    expect(repo.submit.mock.calls[1]).toEqual(repo.submit.mock.calls[0]);
    expect(Object.isFrozen(repo.submit.mock.calls[0]?.[3])).toBe(true);
    expect(input.reason).toBe("  Independent review  ");
  });
  it.each(["payroll.read", "payroll.review", "payroll.amend.review", "payroll.amend.publish"])(
    "does not submit under %s",
    async (permission) => {
      const repo = fixture();
      expect(
        await new SubmitPayrollAmendmentReview(repo).execute(
          { ...access, permissions: [permission] },
          amendmentId(),
          operation,
          {
            expectedVersion: 1,
            reviewId: amendmentReviewId(),
            calculationId: amendmentCalculationId,
            reason: "Reviewed",
          },
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(repo.submit).not.toHaveBeenCalled();
    },
  );
  it.each([
    { expectedVersion: -1 },
    { expectedVersion: 999 },
    { expectedVersion: 0.5 },
    { reason: " " },
    { reason: "x".repeat(1001) },
    { reason: "line\nline" },
    { calculationId: "invalid" },
    { reviewId: "invalid" },
  ])("bounds submission %j without writing", async (change) => {
    const repo = fixture(),
      input = {
        expectedVersion: 1,
        reviewId: amendmentReviewId(),
        calculationId: amendmentCalculationId,
        reason: "Reviewed",
        ...change,
      };
    expect(
      (
        await new SubmitPayrollAmendmentReview(repo).execute(
          access,
          amendmentId(),
          operation,
          input,
          signal,
        )
      ).ok,
    ).toBe(false);
    expect(repo.submit).not.toHaveBeenCalled();
  });
  for (const action of ["APPROVE", "REJECT", "WITHDRAW"] as const) {
    const actor = {
        ...access,
        permissions: [action === "WITHDRAW" ? "payroll.amend" : "payroll.amend.review"],
      },
      method = action === "WITHDRAW" ? "withdraw" : "decide",
      input = {
        amendmentId: amendmentId(),
        expectedVersion: 2,
        expectedReviewVersion: 0,
        expectedApprovalVersion: 0,
        reason: "Reviewed",
        decision: action === "WITHDRAW" ? ("APPROVE" as const) : action,
      };
    const useCase = (repo: PayrollAmendmentReviewRepository) =>
      action === "WITHDRAW"
        ? new WithdrawPayrollAmendmentReview(repo)
        : new DecidePayrollAmendmentReview(repo);
    it(`${action} sends all observed versions without replacing them on a retry`, async () => {
      const repo = fixture(),
        command = useCase(repo),
        original = { ...input, amendmentId: amendmentId().toUpperCase(), reason: "  Reviewed  " };
      await command.execute(actor, amendmentReviewId().toUpperCase(), operation, original, signal);
      await command.execute(actor, amendmentReviewId(), operation, original, signal);
      expect(repo.get).not.toHaveBeenCalled();
      expect(repo.list).not.toHaveBeenCalled();
      expect(repo[method]).toHaveBeenCalledTimes(2);
      expect(repo[method].mock.calls[0]?.slice(0, 3)).toEqual([
        access.companyId,
        amendmentReviewId(),
        operation,
      ]);
      expect(repo[method].mock.calls[0]?.[3]).toEqual({
        amendmentId: amendmentId(),
        expectedVersion: 2,
        expectedReviewVersion: 0,
        expectedApprovalVersion: 0,
        reason: "Reviewed",
        ...(action === "WITHDRAW" ? {} : { decision: action }),
      });
      expect(repo[method].mock.calls[1]).toEqual(repo[method].mock.calls[0]);
      expect(Object.isFrozen(repo[method].mock.calls[0]?.[3])).toBe(true);
    });
    it.each([
      { expectedVersion: 999 },
      { expectedReviewVersion: -1 },
      { expectedReviewVersion: 10 },
      { expectedReviewVersion: 0.5 },
      { expectedApprovalVersion: -1 },
      { expectedApprovalVersion: 0.5 },
      { expectedApprovalVersion: Number.MAX_SAFE_INTEGER + 1 },
      { amendmentId: "other-company" },
      { reason: " " },
    ])(`${action} validates %j before the write`, async (change) => {
      const repo = fixture();
      expect(
        (
          await useCase(repo).execute(
            actor,
            amendmentReviewId(),
            operation,
            { ...input, ...change },
            signal,
          )
        ).ok,
      ).toBe(false);
      expect(repo[method]).not.toHaveBeenCalled();
    });
    it(`${action} preserves server failures and rejects unauthorized or cancelled ownership`, async () => {
      const repo = fixture(),
        command = useCase(repo),
        owner = new AbortController();
      repo[method].mockResolvedValueOnce(failed("self_approval_denied"));
      expect(await command.execute(actor, amendmentReviewId(), operation, input, signal)).toEqual(
        failed("self_approval_denied"),
      );
      const other = {
        ...actor,
        permissions: [action === "WITHDRAW" ? "payroll.amend.review" : "payroll.amend"],
      };
      expect(await command.execute(other, amendmentReviewId(), operation, input, signal)).toEqual(
        failed("access_denied"),
      );
      owner.abort();
      expect(() =>
        command.execute(actor, amendmentReviewId(), operation, input, owner.signal),
      ).toThrow();
      expect(repo[method]).toHaveBeenCalledTimes(1);
    });
  }
});
