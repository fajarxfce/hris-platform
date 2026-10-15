import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollReview } from "../../domain/entities/payroll-review";
import type { PayrollReviewDetails } from "../../domain/entities/payroll-review-details";
import type { PayrollReviewPreparation } from "../../domain/entities/payroll-review-preparation";
import type {
  PayrollReviewDetailsDto,
  PayrollReviewDto,
  PayrollReviewPreparationDto,
  PayrollReviewsDto,
} from "../models/payroll-review-dto";
import { toPayrollCalculationFailure, toPayrollRun } from "./payroll-calculation-mapper";
import { toPayrollReviewApproval } from "./payroll-review-approval-mapper";
export function toPayrollReview(raw: PayrollReviewDto, company: CompanyId): PayrollReview {
  if (raw.totals.employeeCount < 1) throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId: company,
    id: raw.id.toLowerCase(),
    runId: raw.runId.toLowerCase(),
    approvalId: raw.approvalId.toLowerCase(),
    submittedBy: raw.submittedBy.toLowerCase(),
    totals: Object.freeze({ ...raw.totals }),
  });
}
export function toPayrollReviews(
  raw: PayrollReviewsDto,
  company: CompanyId,
  run: string,
): readonly PayrollReview[] {
  const reviews = raw.items.map((item) => toPayrollReview(item, company));
  if (
    raw.nextCursor !== null ||
    new Set(reviews.map((row) => row.id)).size !== reviews.length ||
    reviews.some((row, index) => row.runId !== run || row.number !== index + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(reviews);
}
export function toPayrollReviewPreparation(
  raw: PayrollReviewPreparationDto,
  company: CompanyId,
  id: string,
): PayrollReviewPreparation {
  const run = toPayrollRun(raw.run, company),
    latestReview = raw.latestReview ? toPayrollReview(raw.latestReview, company) : null;
  if (
    run.id !== id ||
    raw.totals.employeeCount !== run.succeeded ||
    (latestReview !== null && latestReview.runId !== id) ||
    (raw.availableActions.includes("SUBMIT") &&
      (run.status !== "CALCULATED" ||
        run.failed !== 0 ||
        run.processed !== run.totalEmployees ||
        raw.blockingIssues.length > 0 ||
        (latestReview !== null && latestReview.status !== "WITHDRAWN")))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    run,
    totals: Object.freeze({ ...raw.totals }),
    latestReview,
    blockingIssues: Object.freeze(raw.blockingIssues.map(toPayrollCalculationFailure)),
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
export function toPayrollReviewDetails(
  raw: PayrollReviewDetailsDto,
  company: CompanyId,
  id: string,
): PayrollReviewDetails {
  const review = toPayrollReview(raw.review, company),
    run = toPayrollRun(raw.run, company),
    approval = toPayrollReviewApproval(raw.approval);
  const changes = raw.changes.map((row) =>
      Object.freeze({
        ...row,
        actorId: row.actorId.toLowerCase(),
        decidingFor: row.decidingFor?.toLowerCase() ?? null,
      }),
    ),
    last = changes.at(-1);
  const accounts = raw.accounts.map((row) => Object.freeze({ ...row, id: row.id.toLowerCase() }));
  const expectedStatus =
    approval.status === "CANCELLED"
      ? "WITHDRAWN"
      : approval.status === "BLOCKED"
        ? "PENDING"
        : approval.status;
  const partyIds = new Set([
    ...approval.stages.flat(),
    ...changes.flatMap((row) => [row.actorId, ...(row.decidingFor ? [row.decidingFor] : [])]),
    run.actorId,
    review.submittedBy,
  ]);
  if (
    review.id !== id ||
    review.runId !== run.id ||
    review.approvalId !== approval.id ||
    review.submittedBy !== approval.authorId ||
    !approval.excludedAccountIds.includes(run.actorId) ||
    review.status !== expectedStatus ||
    review.totals.employeeCount !== run.totalEmployees ||
    changes.length !== review.version + 1 ||
    changes.some(
      (row, index) =>
        row.revision !== index ||
        row.approvalVersion > approval.version ||
        (row.step !== null && row.step >= approval.stages.length),
    ) ||
    last?.status !== review.status ||
    new Set(accounts.map((row) => row.id)).size !== accounts.length ||
    accounts.some((row) => !partyIds.has(row.id)) ||
    raw.availableActions.some(
      (action) =>
        run.status !== "CALCULATED" ||
        run.version !== review.runVersion ||
        (action === "WITHDRAW"
          ? raw.finalizationActive || !["PENDING", "APPROVED"].includes(review.status)
          : review.status !== "PENDING" || approval.status !== "PENDING"),
    ) ||
    new Set(raw.availableActions).size !== raw.availableActions.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    review,
    run,
    approval,
    changes: Object.freeze(changes),
    accounts: Object.freeze(accounts),
    finalizationActive: raw.finalizationActive,
    availableActions: Object.freeze([...raw.availableActions]),
  });
}
