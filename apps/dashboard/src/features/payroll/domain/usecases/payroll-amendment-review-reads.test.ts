import { describe, expect, it, vi } from "vitest";
import {
  amendmentReview,
  amendmentReviewDetails,
  amendmentReviewId,
} from "../../../../../tests/fixtures/payroll-amendment-reviews";
import { amendmentDetails, amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import { toPayrollAmendmentReviewDetails } from "../../data/mappers/payroll-amendment-review-mapper";
import { toPayrollAmendmentReviewSummary } from "../../data/mappers/payroll-amendment-review-summary-mapper";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";
import { LoadPayrollAmendmentReview } from "./load-payroll-amendment-review";
import { LoadPayrollAmendmentReviewAction } from "./load-payroll-amendment-review-action";
import { LoadPayrollAmendmentReviewSubmission } from "./load-payroll-amendment-review-submission";
import { LoadPayrollAmendmentReviews } from "./load-payroll-amendment-reviews";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend"],
  },
  signal = new AbortController().signal;
function fixture() {
  const review = toPayrollAmendmentReviewDetails(
      amendmentReviewDetails(),
      access.companyId,
      amendmentReviewId(),
    ),
    current = toPayrollAmendmentDetails(amendmentDetails(), access.companyId, amendmentId());
  const amendments = {
    get: vi.fn<PayrollAmendmentRepository["get"]>().mockResolvedValue(success(current)),
    list: vi.fn<PayrollAmendmentRepository["list"]>(),
    history: vi.fn<PayrollAmendmentRepository["history"]>(),
    revision: vi.fn<PayrollAmendmentRepository["revision"]>(),
    calculation: vi.fn<PayrollAmendmentRepository["calculation"]>(),
    save: vi.fn<PayrollAmendmentRepository["save"]>(),
    calculate: vi.fn<PayrollAmendmentRepository["calculate"]>(),
    cancel: vi.fn<PayrollAmendmentRepository["cancel"]>(),
  };
  const reviews = {
    get: vi.fn<PayrollAmendmentReviewRepository["get"]>().mockResolvedValue(success(review)),
    list: vi.fn<PayrollAmendmentReviewRepository["list"]>().mockResolvedValue(success([])),
    submit: vi.fn<PayrollAmendmentReviewRepository["submit"]>(),
    decide: vi.fn<PayrollAmendmentReviewRepository["decide"]>(),
    withdraw: vi.fn<PayrollAmendmentReviewRepository["withdraw"]>(),
  };
  return { review, current, amendments, reviews };
}
describe("amendment review acquisition policy", () => {
  it.each(["payroll.amend", "payroll.amend.review", "payroll.amend.publish"])(
    "allows private inspection with %s independently",
    async (permission) => {
      const f = fixture(),
        actor = { ...access, permissions: [permission] };
      expect(
        (
          await new LoadPayrollAmendmentReviews(f.amendments, f.reviews).execute(
            actor,
            amendmentId().toUpperCase(),
            signal,
          )
        ).ok,
      ).toBe(true);
      expect(
        (
          await new LoadPayrollAmendmentReview(f.reviews).execute(
            actor,
            amendmentReviewId().toUpperCase(),
            signal,
          )
        ).ok,
      ).toBe(true);
      expect(f.reviews.get).toHaveBeenCalledExactlyOnceWith(
        access.companyId,
        amendmentReviewId(),
        signal,
      );
    },
  );
  it.each(["payroll.read", "payroll.review", "approvals.read"])(
    "does not expose private amendments through %s",
    async (permission) => {
      const f = fixture(),
        actor = { ...access, permissions: [permission] };
      expect(
        await new LoadPayrollAmendmentReviews(f.amendments, f.reviews).execute(
          actor,
          amendmentId(),
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(
        await new LoadPayrollAmendmentReview(f.reviews).execute(actor, amendmentReviewId(), signal),
      ).toEqual(failed("access_denied"));
      expect(f.amendments.get).not.toHaveBeenCalled();
      expect(f.reviews.get).not.toHaveBeenCalled();
      expect(f.reviews.list).not.toHaveBeenCalled();
    },
  );
  it("rejects mixed directory snapshots when a submission or decision races the list", async () => {
    const f = fixture(),
      load = new LoadPayrollAmendmentReviews(f.amendments, f.reviews),
      summary = toPayrollAmendmentReviewSummary(amendmentReview());
    f.reviews.list.mockResolvedValue(success([summary]));
    expect(await load.execute(access, amendmentId(), signal)).toEqual(
      failed("payroll_amendment_review_not_current"),
    );
    f.amendments.get.mockResolvedValue(success({ ...f.current, latestReview: summary }));
    expect((await load.execute(access, amendmentId(), signal)).ok).toBe(true);
    for (const changed of [
      { ...summary, version: 1 },
      { ...summary, status: "APPROVED" as const },
      { ...summary, id: amendmentReviewId(2) },
    ]) {
      f.reviews.list.mockResolvedValueOnce(success([changed]));
      expect(await load.execute(access, amendmentId(), signal)).toEqual(
        failed("payroll_amendment_review_not_current"),
      );
    }
  });
  it("requires a current successful calculation and an author grant for submission", async () => {
    const f = fixture(),
      load = new LoadPayrollAmendmentReviewSubmission(f.amendments);
    expect((await load.execute(access, amendmentId(), signal)).ok).toBe(true);
    for (const changed of [
      { ...f.current, latestCalculation: null },
      { ...f.current, availableActions: [] },
      {
        ...f.current,
        amendment: {
          ...f.current.amendment,
          summary: { ...f.current.amendment.summary, status: "DRAFT" as const },
        },
      },
    ]) {
      f.amendments.get.mockResolvedValueOnce(success(changed));
      expect(await load.execute(access, amendmentId(), signal)).toEqual(
        failed("payroll_amendment_calculation_required"),
      );
    }
    const calls = f.amendments.get.mock.calls.length;
    expect(
      await load.execute(
        { ...access, permissions: ["payroll.amend.review"] },
        amendmentId(),
        signal,
      ),
    ).toEqual(failed("access_denied"));
    expect(f.amendments.get).toHaveBeenCalledTimes(calls);
  });
  it.each(["APPROVE", "REJECT", "WITHDRAW"] as const)(
    "%s intersects independent authority with live server actions",
    async (action) => {
      const f = fixture(),
        load = new LoadPayrollAmendmentReviewAction(f.reviews),
        permission = action === "WITHDRAW" ? "payroll.amend" : "payroll.amend.review",
        actor = { ...access, permissions: [permission] };
      f.reviews.get.mockResolvedValueOnce(success({ ...f.review, availableActions: [action] }));
      expect((await load.execute(actor, amendmentReviewId(), action, signal)).ok).toBe(true);
      f.reviews.get.mockResolvedValueOnce(success({ ...f.review, availableActions: [] }));
      expect(await load.execute(actor, amendmentReviewId(), action, signal)).toEqual(
        failed("payroll_amendment_review_action_unavailable"),
      );
      expect(
        await load.execute(
          { ...access, permissions: ["payroll.amend.publish"] },
          amendmentReviewId(),
          action,
          signal,
        ),
      ).toEqual(failed("access_denied"));
      expect(f.reviews.get).toHaveBeenCalledTimes(2);
    },
  );
  it("stops before a second source and never returns acquired private details after cancellation", async () => {
    const f = fixture(),
      owner = new AbortController();
    f.amendments.get.mockImplementationOnce(async () => {
      owner.abort();
      return success(f.current);
    });
    await expect(
      new LoadPayrollAmendmentReviews(f.amendments, f.reviews).execute(
        access,
        amendmentId(),
        owner.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.reviews.list).not.toHaveBeenCalled();
    const second = new AbortController();
    f.reviews.list.mockImplementationOnce(async () => {
      second.abort();
      return success([]);
    });
    await expect(
      new LoadPayrollAmendmentReviews(f.amendments, f.reviews).execute(
        access,
        amendmentId(),
        second.signal,
      ),
    ).rejects.toMatchObject({ name: "AbortError" });
    const detail = new AbortController();
    f.reviews.get.mockImplementationOnce(async () => {
      detail.abort();
      return success(f.review);
    });
    await expect(
      new LoadPayrollAmendmentReview(f.reviews).execute(access, amendmentReviewId(), detail.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
  it("preserves denied reads and rejects malformed route identifiers before I/O", async () => {
    const f = fixture(),
      list = new LoadPayrollAmendmentReviews(f.amendments, f.reviews);
    expect(await list.execute(access, "private/invalid", signal)).toEqual(
      failed("payroll_amendment_not_found"),
    );
    expect(
      await new LoadPayrollAmendmentReview(f.reviews).execute(access, "invalid", signal),
    ).toEqual(failed("payroll_amendment_review_not_found"));
    expect(f.amendments.get).not.toHaveBeenCalled();
    expect(f.reviews.get).not.toHaveBeenCalled();
    f.amendments.get.mockResolvedValueOnce(failed("mfa_required"));
    expect(await list.execute(access, amendmentId(), signal)).toEqual(failed("mfa_required"));
    expect(f.reviews.list).not.toHaveBeenCalled();
    f.reviews.list.mockResolvedValueOnce(failed("access_denied"));
    expect(await list.execute(access, amendmentId(), signal)).toEqual(failed("access_denied"));
  });
});
