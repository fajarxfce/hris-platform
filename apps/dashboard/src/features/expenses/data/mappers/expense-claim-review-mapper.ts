import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseClaimReview } from "../../domain/entities/expense-claim-review";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import type { ExpenseClaimReviewDto } from "../models/expense-claim-dto";
import { toExpenseClaim } from "./expense-claim-mapper";
export function toExpenseClaimReview(
  raw: ExpenseClaimReviewDto,
  company: CompanyId,
  id: ExpenseClaimId,
  after: string | null,
): ExpenseClaimReview {
  const claim = toExpenseClaim(raw.claim, company, id);
  let previous = after === null ? claim.version + 1 : Number(after);
  if (after !== null && previous > claim.version) throw new InvalidHttpResponseError();
  const history = raw.history.items.map((change) => {
    if (
      change.version !== previous - 1 ||
      change.draftRevision > claim.draftRevision ||
      (change.version === claim.version &&
        (change.status !== claim.status || change.draftRevision !== claim.draftRevision))
    )
      throw new InvalidHttpResponseError();
    previous = change.version;
    return Object.freeze({
      ...change,
      actorId: change.actorId.toLowerCase(),
      submissionId: (change.submissionId?.toLowerCase() as ExpenseSubmissionId | undefined) ?? null,
    });
  });
  const cursor = raw.history.nextCursor;
  if (
    (cursor === null && previous !== 0) ||
    (cursor !== null && (history.length !== 20 || cursor !== String(previous) || previous <= 0))
  )
    throw new InvalidHttpResponseError();
  const submissions = raw.submissions.items.map((row, index) => {
    if (
      row.number !== claim.submissionCount - index ||
      row.draftRevision > claim.draftRevision ||
      (index === 0 && row.id.toLowerCase() !== claim.latestSubmissionId)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...row,
      id: row.id.toLowerCase() as ExpenseSubmissionId,
      approvalId: row.approvalId.toLowerCase(),
      submittedBy: row.submittedBy.toLowerCase(),
    });
  });
  if (
    raw.submissions.nextCursor !== null ||
    submissions.length !== claim.submissionCount ||
    new Set(submissions.map((item) => item.id)).size !== submissions.length
  )
    throw new InvalidHttpResponseError();
  const payments = raw.payments.map((row) => {
    if (
      (["PENDING", "SUCCEEDED", "FAILED"].includes(row.status) && row.releasedAt === null) ||
      (["SUCCEEDED", "FAILED"].includes(row.status) && row.settledAt === null)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...row,
      itemId: row.itemId.toLowerCase(),
      batchId: row.batchId.toLowerCase(),
    });
  });
  if (new Set(payments.map((item) => item.itemId)).size !== payments.length)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    claim,
    history: Object.freeze({ items: Object.freeze(history), nextCursor: cursor }),
    submissions: Object.freeze(submissions),
    payments: Object.freeze(payments),
  });
}
