import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentReviewSummary } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentReviewDetails } from "../../domain/entities/payroll-amendment-review-details";
import type {
  PayrollAmendmentReviewDetailsDto,
  PayrollAmendmentReviewSummaryDto,
} from "../models/payroll-amendment-review-dto";
import { toPayrollAmendmentCalculationSummary } from "./payroll-amendment-calculation-mapper";
import { toPayrollAmendmentSummary } from "./payroll-amendment-mapper";
import { toPayrollAmendmentReviewSummary } from "./payroll-amendment-review-summary-mapper";
import { toPayrollReviewApproval } from "./payroll-review-approval-mapper";

export function toPayrollAmendmentReviews(
  raw: readonly PayrollAmendmentReviewSummaryDto[],
  amendment: string,
): readonly PayrollAmendmentReviewSummary[] {
  const rows = raw.map(toPayrollAmendmentReviewSummary);
  if (
    new Set(rows.map((row) => row.id)).size !== rows.length ||
    rows.some((row, index) => row.amendmentId !== amendment || row.number !== index + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(rows);
}

export function toPayrollAmendmentReviewDetails(
  raw: PayrollAmendmentReviewDetailsDto,
  company: CompanyId,
  id: string,
): PayrollAmendmentReviewDetails {
  const review = toPayrollAmendmentReviewSummary(raw.review),
    amendment = toPayrollAmendmentSummary(raw.amendment, company),
    calculation = toPayrollAmendmentCalculationSummary(raw.calculation, amendment.id),
    approval = toPayrollReviewApproval(raw.approval),
    history = raw.history.map((row) =>
      Object.freeze({
        ...row,
        actorId: row.actorId.toLowerCase(),
        decidingFor: row.decidingFor?.toLowerCase() ?? null,
      }),
    ),
    accounts = raw.accounts.map((row) => Object.freeze({ ...row, id: row.id.toLowerCase() })),
    first = history[0],
    last = history.at(-1);
  const status =
    approval.status === "CANCELLED"
      ? "WITHDRAWN"
      : approval.status === "BLOCKED"
        ? "PENDING"
        : approval.status;
  const partyIds = new Set([
    ...approval.stages.flat(),
    ...history.flatMap((row) => (row.decidingFor ? [row.actorId, row.decidingFor] : [row.actorId])),
    review.submittedBy,
  ]);
  if (
    review.id !== id ||
    review.amendmentId !== amendment.id ||
    review.calculationId !== calculation.id ||
    calculation.status !== "SUCCEEDED" ||
    calculation.draftVersion >= review.amendmentVersion ||
    review.amendmentVersion >= amendment.version ||
    review.approvalId !== approval.id ||
    review.submittedBy !== approval.authorId ||
    review.status !== status ||
    history.length !== review.version + 1 ||
    first?.action !== "SUBMITTED" ||
    first.status !== "PENDING" ||
    first.amendmentRevision !== review.amendmentVersion + 1 ||
    first.actorId !== review.submittedBy ||
    first.step !== null ||
    first.approvalVersion !== 0 ||
    first.decidingFor !== null ||
    first.reason !== review.reason ||
    Date.parse(first.recordedAt) !== Date.parse(review.submittedAt) ||
    last?.status !== review.status ||
    history.some(
      (row, index) =>
        row.revision !== index ||
        row.approvalVersion > approval.version ||
        row.amendmentRevision > amendment.version ||
        (row.step !== null && row.step >= approval.stages.length) ||
        (index > 0 &&
          (row.action === "SUBMITTED" ||
            (row.action === "WITHDRAWN"
              ? row.status !== "WITHDRAWN" ||
                row.step !== null ||
                row.decidingFor !== null ||
                !["PENDING", "APPROVED"].includes(history[index - 1]?.status ?? "")
              : history[index - 1]?.status !== "PENDING" ||
                row.step === null ||
                row.decidingFor === null ||
                (row.action === "REJECTED"
                  ? row.status !== "REJECTED"
                  : !["PENDING", "APPROVED"].includes(row.status))) ||
            row.amendmentRevision <= (history[index - 1]?.amendmentRevision ?? 0) ||
            row.approvalVersion <= (history[index - 1]?.approvalVersion ?? -1) ||
            Date.parse(row.recordedAt) < Date.parse(history[index - 1]?.recordedAt ?? ""))),
    ) ||
    new Set(accounts.map((row) => row.id)).size !== accounts.length ||
    accounts.some((row) => !partyIds.has(row.id)) ||
    new Set(raw.availableActions).size !== raw.availableActions.length ||
    raw.availableActions.some(
      (action) =>
        amendment.latestReviewId !== review.id ||
        amendment.latestCalculationId !== calculation.id ||
        !["IN_REVIEW", "APPROVED"].includes(amendment.status) ||
        (action === "WITHDRAW"
          ? !["PENDING", "APPROVED"].includes(review.status)
          : review.status !== "PENDING" || approval.status !== "PENDING"),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    review,
    amendment,
    calculation,
    approval,
    history: Object.freeze(history),
    accounts: Object.freeze(accounts),
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
