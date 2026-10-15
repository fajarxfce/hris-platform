import type { PayrollAmendmentDetailsDto } from "../../src/features/payroll/data/models/payroll-amendment-dto";
import type { PayrollAmendmentPublicationDetailsDto } from "../../src/features/payroll/data/models/payroll-amendment-publication-dto";
import { amendmentReviewDetails } from "./payroll-amendment-reviews";
import { amendmentDetails, amendmentSummary } from "./payroll-amendments";
import { reviewFirst } from "./payroll-reviews";

export const amendmentPublicationId = (index = 1) =>
  `fc100000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const amendmentPublisherId = "fc300000-0000-4000-8000-000000000001";
export function approvedAmendmentReview() {
  const raw = amendmentReviewDetails(),
    initial = raw.history[0];
  if (!initial) throw new Error("Submitted review required");
  raw.review.version = 1;
  raw.review.status = "APPROVED";
  raw.amendment.version = 3;
  raw.amendment.status = "APPROVED";
  raw.amendment.action = "APPROVED";
  raw.amendment.actorId = reviewFirst;
  raw.amendment.recordedAt = "2026-10-01T01:00:03Z";
  raw.amendment.reason = "Independent correction review";
  raw.approval.version = 1;
  raw.approval.status = "APPROVED";
  raw.approval.stages = [[reviewFirst]];
  raw.accounts = raw.accounts.filter(
    (account) => account.id === reviewFirst || account.id === raw.review.submittedBy,
  );
  raw.history.push({
    ...initial,
    revision: 1,
    amendmentRevision: 3,
    action: "APPROVED",
    status: "APPROVED",
    approvalVersion: 1,
    step: 0,
    actorId: reviewFirst,
    decidingFor: reviewFirst,
    reason: raw.amendment.reason,
    recordedAt: raw.amendment.recordedAt,
  });
  raw.availableActions = [];
  return raw;
}
export function approvedAmendmentDetails(): PayrollAmendmentDetailsDto {
  const original = amendmentDetails(),
    reviewed = approvedAmendmentReview();
  const submitted = {
    ...amendmentSummary(2),
    status: "IN_REVIEW" as const,
    action: "SUBMITTED" as const,
    latestReviewId: reviewed.review.id,
    reason: reviewed.review.reason,
  };
  return {
    ...original,
    amendment: { ...original.amendment, summary: reviewed.amendment },
    latestReview: reviewed.review,
    availableActions: [],
    history: {
      items: [...original.history.items, submitted, reviewed.amendment],
      nextCursor: null,
    },
  };
}
export function amendmentPublicationDetails(
  status: "PENDING" | "PUBLISHED" | "STOPPED" | "EXHAUSTED" = "PENDING",
): PayrollAmendmentPublicationDetailsDto {
  const reviewed = approvedAmendmentReview(),
    pending = status === "PENDING" || status === "EXHAUSTED",
    published = status === "PUBLISHED",
    actor = amendmentPublisherId,
    id = amendmentPublicationId(),
    jobId = "fc200000-0000-4000-8000-000000000001";
  return {
    publication: {
      id,
      amendmentId: reviewed.amendment.id,
      reviewId: reviewed.review.id,
      calculationId: reviewed.calculation.id,
      amendmentVersion: 3,
      reviewVersion: 1,
      approvalVersion: 1,
      number: 1,
      jobId,
      actorId: actor,
      requestedAt: "2026-10-01T01:00:04Z",
      reason: "Publish independently reviewed correction",
      status: pending ? "PENDING" : status,
      completedAt: pending ? null : "2026-10-01T01:00:05Z",
      failureCode: status === "STOPPED" ? "job_failed" : null,
      completedBy: pending ? null : actor,
    },
    amendment: {
      ...reviewed.amendment,
      version: pending ? 4 : 5,
      status: pending ? "PUBLISHING" : published ? "PUBLISHED" : "APPROVED",
      action: pending ? "PUBLICATION_STARTED" : published ? "PUBLISHED" : "PUBLICATION_STOPPED",
      latestPublicationId: id,
      actorId: actor,
      recordedAt: pending ? "2026-10-01T01:00:04Z" : "2026-10-01T01:00:05Z",
      reason: "Publish independently reviewed correction",
    },
    job: {
      jobId,
      status: status === "PENDING" ? "QUEUED" : published ? "SUCCEEDED" : "FAILED",
      completedItems: published ? 1 : 0,
      totalItems: 1,
      attempts: status === "PENDING" ? 0 : 1,
      version: status === "PENDING" ? 0 : 2,
      cancellationRequested: false,
      failureCode: status === "STOPPED" || status === "EXHAUSTED" ? "job_failed" : null,
    },
    availableActions: status === "EXHAUSTED" ? ["RECOVER"] : [],
  };
}
