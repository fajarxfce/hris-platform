import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseClaimId } from "../../domain/entities/expense-claim";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import type { ExpenseSubmissionId } from "../../domain/entities/expense-submission-summary";
import { expenseAmountMinor } from "../../domain/policies/expense-amount";
import type { ExpenseSubmissionDto } from "../models/expense-submission-dto";
import { toExpensePolicyTerms } from "./expense-policy-mapper";
export function toExpenseSubmission(
  raw: ExpenseSubmissionDto,
  companyId: CompanyId,
  id: ExpenseSubmissionId,
): ExpenseSubmission {
  const lines = raw.lines.map((line) => {
    const amount = expenseAmountMinor(line.amount);
    if (
      amount === null ||
      amount <= 0n ||
      line.occurredOn < "1900-01-01" ||
      line.occurredOn > "2200-12-31" ||
      line.category.appliedRevision > line.category.version
    )
      throw new InvalidHttpResponseError();
    const receipts = line.receipts.map((receipt) =>
      Object.freeze({ ...receipt, revisionId: receipt.revisionId.toLowerCase() }),
    );
    if (new Set(receipts.map((receipt) => receipt.revisionId)).size !== receipts.length)
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...line,
      id: line.id.toLowerCase(),
      receipts: Object.freeze(receipts),
      category: Object.freeze({
        ...toExpensePolicyTerms(line.category),
        id: line.category.id.toLowerCase(),
        code: line.category.code,
        effectiveFrom: line.category.effectiveFrom,
        revision: line.category.appliedRevision,
      }),
      costCenter: line.costCenter
        ? Object.freeze({ ...line.costCenter, id: line.costCenter.id.toLowerCase() })
        : null,
    });
  });
  if (
    raw.id.toLowerCase() !== id ||
    new Set(lines.map((line) => line.id)).size !== lines.length ||
    lines.reduce((sum, line) => sum + (expenseAmountMinor(line.amount) ?? 0n), 0n) !==
      expenseAmountMinor(raw.totalAmount)
  )
    throw new InvalidHttpResponseError();
  const makers = raw.makerIds.map((value) => value.toLowerCase());
  const stages = raw.approval.stages.map((stage) => {
    const ids = stage.map((value) => value.toLowerCase());
    if (new Set(ids).size !== ids.length) throw new InvalidHttpResponseError();
    return Object.freeze(ids);
  });
  if (
    new Set(makers).size !== makers.length ||
    raw.approval.currentStep > stages.length ||
    (["PENDING", "BLOCKED"].includes(raw.approval.status) &&
      raw.approval.currentStep >= stages.length)
  )
    throw new InvalidHttpResponseError();
  let previousVersion = -1;
  const reviews = raw.reviews.map((review) => {
    if (
      review.claimVersion <= previousVersion ||
      review.claimVersion > raw.claimVersion ||
      review.approvalVersion > raw.approval.version ||
      review.step >= stages.length ||
      new Set(review.duplicateDigests).size !== review.duplicateDigests.length
    )
      throw new InvalidHttpResponseError();
    previousVersion = review.claimVersion;
    return Object.freeze({
      ...review,
      actorId: review.actorId.toLowerCase(),
      decidingFor: review.decidingFor.toLowerCase(),
      duplicateDigests: Object.freeze([...review.duplicateDigests]),
    });
  });
  const hashes = new Set(lines.flatMap((line) => line.receipts).map((receipt) => receipt.sha256));
  if (
    new Set(raw.duplicateReceiptDigests).size !== raw.duplicateReceiptDigests.length ||
    raw.duplicateReceiptDigests.some((digest) => !hashes.has(digest))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id,
    companyId,
    claimId: raw.claimId.toLowerCase() as ExpenseClaimId,
    employmentId: raw.employmentId.toLowerCase(),
    requesterId: raw.requesterId?.toLowerCase() ?? null,
    submittedBy: raw.submittedBy.toLowerCase(),
    approvalId: raw.approval.id.toLowerCase(),
    makerIds: Object.freeze(makers),
    lines: Object.freeze(lines),
    reviews: Object.freeze(reviews),
    approval: Object.freeze({
      ...raw.approval,
      id: raw.approval.id.toLowerCase(),
      templateId: raw.approval.templateId.toLowerCase(),
      stages: Object.freeze(stages),
    }),
    duplicateReceiptDigests: Object.freeze([...raw.duplicateReceiptDigests]),
  });
}
