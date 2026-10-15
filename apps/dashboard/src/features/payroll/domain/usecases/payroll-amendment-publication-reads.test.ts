import { describe, expect, it, vi } from "vitest";
import {
  amendmentPublicationDetails,
  amendmentPublicationId,
  approvedAmendmentDetails,
  approvedAmendmentReview,
} from "../../../../../tests/fixtures/payroll-amendment-publications";
import { amendmentId } from "../../../../../tests/fixtures/payroll-amendments";
import { calculationCompany } from "../../../../../tests/fixtures/payroll-calculations";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { toPayrollAmendmentDetails } from "../../data/mappers/payroll-amendment-mapper";
import { toPayrollAmendmentPublicationDetails } from "../../data/mappers/payroll-amendment-publication-mapper";
import { toPayrollAmendmentReviewDetails } from "../../data/mappers/payroll-amendment-review-mapper";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";
import { LoadPayrollAmendmentPublication } from "./load-payroll-amendment-publication";
import { LoadPayrollAmendmentPublications } from "./load-payroll-amendment-publications";
import { ReviewPayrollAmendmentPublication } from "./review-payroll-amendment-publication";
import { ReviewPayrollAmendmentPublicationRecovery } from "./review-payroll-amendment-publication-recovery";

const access: CompanyAccess = {
    companyId: calculationCompany as CompanyId,
    permissions: ["payroll.amend.publish"],
  },
  signal = new AbortController().signal;
function fixture() {
  const current = toPayrollAmendmentDetails(
      approvedAmendmentDetails(),
      access.companyId,
      amendmentId(),
    ),
    rawReview = approvedAmendmentReview(),
    review = toPayrollAmendmentReviewDetails(rawReview, access.companyId, rawReview.review.id),
    completed = toPayrollAmendmentPublicationDetails(
      amendmentPublicationDetails("PUBLISHED"),
      access.companyId,
      amendmentId(),
    ),
    exhausted = toPayrollAmendmentPublicationDetails(
      amendmentPublicationDetails("EXHAUSTED"),
      access.companyId,
      amendmentId(),
    );
  const amendments = {
      get: vi.fn<PayrollAmendmentRepository["get"]>().mockResolvedValue(success(current)),
      list: vi.fn<PayrollAmendmentRepository["list"]>(),
      history: vi.fn<PayrollAmendmentRepository["history"]>(),
      revision: vi.fn<PayrollAmendmentRepository["revision"]>(),
      calculation: vi.fn<PayrollAmendmentRepository["calculation"]>(),
      save: vi.fn<PayrollAmendmentRepository["save"]>(),
      calculate: vi.fn<PayrollAmendmentRepository["calculate"]>(),
      cancel: vi.fn<PayrollAmendmentRepository["cancel"]>(),
    },
    reviews = {
      get: vi.fn<PayrollAmendmentReviewRepository["get"]>().mockResolvedValue(success(review)),
      list: vi.fn<PayrollAmendmentReviewRepository["list"]>(),
      submit: vi.fn<PayrollAmendmentReviewRepository["submit"]>(),
      decide: vi.fn<PayrollAmendmentReviewRepository["decide"]>(),
      withdraw: vi.fn<PayrollAmendmentReviewRepository["withdraw"]>(),
    },
    publications = {
      list: vi.fn<PayrollAmendmentPublicationRepository["list"]>().mockResolvedValue(success([])),
      start: vi.fn<PayrollAmendmentPublicationRepository["start"]>(),
      recover: vi.fn<PayrollAmendmentPublicationRepository["recover"]>(),
    };
  return {
    current,
    review,
    completed,
    exhausted,
    amendments,
    reviews,
    publications,
    directory: new LoadPayrollAmendmentPublications(amendments, publications),
    detail: new LoadPayrollAmendmentPublication(publications),
    prepare: new ReviewPayrollAmendmentPublication(amendments, reviews, publications),
    recover: new ReviewPayrollAmendmentPublicationRecovery(publications),
  };
}
describe("amendment publication acquisition", () => {
  it.each(["payroll.amend", "payroll.amend.review", "payroll.amend.publish"])(
    "reads with independent %s authority",
    async (permission) => {
      const f = fixture(),
        actor = { ...access, permissions: [permission] };
      const result = await f.directory.execute(actor, amendmentId().toUpperCase(), signal);
      expect(result.ok && result.value.availableActions).toEqual(
        permission === "payroll.amend.publish" ? ["START"] : [],
      );
      f.publications.list.mockResolvedValueOnce(success([f.completed]));
      expect(
        await f.detail.execute(
          actor,
          amendmentId().toUpperCase(),
          amendmentPublicationId().toUpperCase(),
          signal,
        ),
      ).toEqual(success(f.completed));
      expect(f.publications.list).toHaveBeenLastCalledWith(access.companyId, amendmentId(), signal);
    },
  );
  it.each(["payroll.read", "payroll.finalize", "approvals.read"])(
    "does not widen %s to private corrections",
    async (permission) => {
      const f = fixture(),
        actor = { ...access, permissions: [permission] };
      expect(await f.directory.execute(actor, amendmentId(), signal)).toEqual(
        failed("access_denied"),
      );
      expect(
        await f.detail.execute(actor, amendmentId(), amendmentPublicationId(), signal),
      ).toEqual(failed("access_denied"));
      expect(f.amendments.get).not.toHaveBeenCalled();
      expect(f.publications.list).not.toHaveBeenCalled();
    },
  );
  it("accepts the list's newer coherent worker snapshot instead of retrying both sources", async () => {
    const f = fixture();
    f.publications.list.mockResolvedValueOnce(success([f.completed]));
    expect(await f.directory.execute(access, amendmentId(), signal)).toEqual(
      success({ amendment: f.completed.amendment, attempts: [f.completed], availableActions: [] }),
    );
    expect(f.amendments.get).toHaveBeenCalledTimes(1);
    expect(f.publications.list).toHaveBeenCalledTimes(1);
  });
  it("rejects regressed versions, changed employee scope and an omitted known attempt", async () => {
    const f = fixture();
    for (const amendment of [
      { ...f.completed.amendment, version: 2 },
      {
        ...f.completed.amendment,
        employee: { ...f.completed.amendment.employee, id: amendmentId(9) },
      },
      { ...f.completed.amendment, firstAssessmentId: amendmentId(9) },
    ]) {
      f.publications.list.mockResolvedValueOnce(success([{ ...f.completed, amendment }]));
      expect(await f.directory.execute(access, amendmentId(), signal)).toEqual(
        failed("payroll_amendment_publication_changed"),
      );
    }
    f.amendments.get.mockResolvedValueOnce(
      success({
        ...f.current,
        amendment: { ...f.current.amendment, summary: f.completed.amendment },
      }),
    );
    expect(await f.directory.execute(access, amendmentId(), signal)).toEqual(
      failed("payroll_amendment_publication_changed"),
    );
  });
  it("prepares one approved calculation and immutable observed review versions without writing", async () => {
    const f = fixture(),
      result = await f.prepare.execute(access, amendmentId(), signal);
    expect(result).toEqual(
      success({
        amendment: f.current.amendment.summary,
        review: f.review.review,
        calculation: f.review.calculation,
        approvalVersion: 1,
        number: 1,
      }),
    );
    expect(result.ok && Object.isFrozen(result.value)).toBe(true);
    expect(f.publications.start).not.toHaveBeenCalled();
  });
  it.each(["payroll.amend", "payroll.amend.review", "payroll.finalize"])(
    "cannot prepare or recover with %s",
    async (permission) => {
      const f = fixture(),
        actor = { ...access, permissions: [permission] };
      expect(await f.prepare.execute(actor, amendmentId(), signal)).toEqual(
        failed("access_denied"),
      );
      expect(
        await f.recover.execute(actor, amendmentId(), amendmentPublicationId(), signal),
      ).toEqual(failed("access_denied"));
      expect(f.amendments.get).not.toHaveBeenCalled();
      expect(f.publications.list).not.toHaveBeenCalled();
    },
  );
  it("requires approved review and successful calculation before reading further", async () => {
    const f = fixture();
    for (const current of [
      { ...f.current, latestReview: null },
      { ...f.current, latestCalculation: null },
      {
        ...f.current,
        amendment: {
          ...f.current.amendment,
          summary: { ...f.current.amendment.summary, status: "CALCULATED" as const },
        },
      },
    ]) {
      f.amendments.get.mockResolvedValueOnce(success(current));
      expect(await f.prepare.execute(access, amendmentId(), signal)).toEqual(
        failed("payroll_amendment_approved_review_required"),
      );
    }
    expect(f.reviews.get).not.toHaveBeenCalled();
    expect(f.publications.list).not.toHaveBeenCalled();
  });
  it("refuses mixed current/review/publication versions rather than silently updating the basis", async () => {
    const f = fixture();
    for (const review of [
      { ...f.review, review: { ...f.review.review, version: 2 } },
      { ...f.review, amendment: { ...f.review.amendment, version: 4 } },
      { ...f.review, approval: { ...f.review.approval, status: "CANCELLED" as const } },
    ]) {
      f.reviews.get.mockResolvedValueOnce(success(review));
      expect(await f.prepare.execute(access, amendmentId(), signal)).toEqual(
        failed("payroll_amendment_publication_changed"),
      );
    }
    expect(f.publications.list).not.toHaveBeenCalled();
    f.publications.list.mockResolvedValueOnce(success([f.exhausted]));
    expect(await f.prepare.execute(access, amendmentId(), signal)).toEqual(
      failed("payroll_amendment_publication_changed"),
    );
  });
  it("uses only a server-offered recovery action and never starts another publication", async () => {
    const f = fixture();
    f.publications.list.mockResolvedValueOnce(success([f.exhausted]));
    expect(
      await f.recover.execute(access, amendmentId(), amendmentPublicationId(), signal),
    ).toEqual(success(f.exhausted));
    f.publications.list.mockResolvedValueOnce(success([{ ...f.exhausted, availableActions: [] }]));
    expect(
      await f.recover.execute(access, amendmentId(), amendmentPublicationId(), signal),
    ).toEqual(failed("payroll_amendment_recovery_unavailable"));
    expect(await f.detail.execute(access, amendmentId(), amendmentPublicationId(), signal)).toEqual(
      failed("payroll_amendment_publication_not_found"),
    );
    expect(f.publications.start).not.toHaveBeenCalled();
    expect(f.publications.recover).not.toHaveBeenCalled();
  });
  it("stops after cancellation at each source without returning private data", async () => {
    for (const step of ["amendment", "review", "publication"] as const) {
      const f = fixture(),
        owner = new AbortController();
      if (step === "amendment")
        f.amendments.get.mockImplementationOnce(async () => {
          owner.abort();
          return success(f.current);
        });
      if (step === "review")
        f.reviews.get.mockImplementationOnce(async () => {
          owner.abort();
          return success(f.review);
        });
      if (step === "publication")
        f.publications.list.mockImplementationOnce(async () => {
          owner.abort();
          return success([]);
        });
      await expect(f.prepare.execute(access, amendmentId(), owner.signal)).rejects.toMatchObject({
        name: "AbortError",
      });
      if (step === "amendment") expect(f.reviews.get).not.toHaveBeenCalled();
      if (step !== "publication") expect(f.publications.list).not.toHaveBeenCalled();
    }
  });
  it("preserves failures and rejects invalid routes before I/O", async () => {
    const f = fixture();
    expect(await f.prepare.execute(access, "invalid", signal)).toEqual(
      failed("payroll_amendment_not_found"),
    );
    expect(await f.detail.execute(access, amendmentId(), "invalid", signal)).toEqual(
      failed("payroll_amendment_publication_not_found"),
    );
    expect(f.amendments.get).not.toHaveBeenCalled();
    expect(f.publications.list).not.toHaveBeenCalled();
    f.amendments.get.mockResolvedValueOnce(failed("mfa_required"));
    expect(await f.prepare.execute(access, amendmentId(), signal)).toEqual(failed("mfa_required"));
    expect(f.reviews.get).not.toHaveBeenCalled();
    f.publications.list.mockResolvedValueOnce(failed("access_denied"));
    expect(await f.detail.execute(access, amendmentId(), amendmentPublicationId(), signal)).toEqual(
      failed("access_denied"),
    );
  });
});
