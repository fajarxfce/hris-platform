import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import { expenseAmountView } from "./expense-amount-view";
export function expenseSubmissionView(
  submission: ExpenseSubmission,
  selectedLine: string | null,
  selectedReview: string | null,
  locale: Locale,
  timezone: string,
) {
  const text = expenseClaimMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const line = submission.lines.find((item) => item.id === selectedLine);
  const review = submission.reviews.find((item) => String(item.claimVersion) === selectedReview);
  return {
    title: submission.title,
    description: submission.description,
    properties: [
      { label: text.employee, value: `${submission.employeeName} · ${submission.employeeNumber}` },
      { label: text.number, value: number.format(submission.number) },
      { label: text.total, value: expenseAmountView(submission.totalAmount, locale) },
      {
        label: `${text.submitted} (${timezone})`,
        value: time.format(new Date(submission.submittedAt)),
      },
      { label: text.approvalStatus, value: text[submission.approval.status] },
      { label: text.currentClaimStatus, value: text[submission.claimStatus] },
      { label: text.latestSubmission, value: submission.current ? text.yes : text.no },
    ],
    lines: submission.lines.map((item) => ({
      id: item.id,
      actionLabel: item.description,
      cells: [
        date.format(new Date(`${item.occurredOn}T00:00:00Z`)),
        item.category.name,
        item.description,
        expenseAmountView(item.amount, locale),
        number.format(item.receipts.length),
      ],
    })),
    duplicate:
      submission.duplicateReceiptDigests.length > 0 ||
      submission.lines.some((item) => item.receipts.some((receipt) => receipt.possibleDuplicate)),
    line: line
      ? {
          properties: [
            { label: text.description, value: line.description },
            { label: text.date, value: date.format(new Date(`${line.occurredOn}T00:00:00Z`)) },
            { label: text.amount, value: expenseAmountView(line.amount, locale) },
            { label: text.category, value: `${line.category.name} (${line.category.code})` },
            { label: text.policyRevision, value: number.format(line.category.revision) },
            {
              label: text.effectiveFrom,
              value: date.format(new Date(`${line.category.effectiveFrom}T00:00:00Z`)),
            },
            {
              label: text.maximumLine,
              value: expenseAmountView(line.category.maximumLineAmount, locale),
            },
            {
              label: text.receiptRequired,
              value: line.category.receiptRequired ? text.yes : text.no,
            },
            {
              label: text.costCenter,
              value: line.costCenter
                ? `${line.costCenter.name} (${line.costCenter.code})`
                : text.none,
            },
          ],
          receipts: line.receipts.map((receipt) => ({
            id: receipt.revisionId,
            name: receipt.fileName,
            size: `${number.format(receipt.size)} B`,
            duplicate:
              receipt.possibleDuplicate ||
              submission.duplicateReceiptDigests.includes(receipt.sha256),
          })),
        }
      : null,
    reviews: submission.reviews.map((entry) => ({
      id: String(entry.claimVersion),
      actionLabel: `${text.stage} ${entry.step + 1}`,
      cells: [
        number.format(entry.step + 1),
        text[entry.decision],
        text[entry.status],
        time.format(new Date(entry.decidedAt)),
      ],
    })),
    review: review
      ? [
          { label: text.stage, value: number.format(review.step + 1) },
          { label: text.decision, value: text[review.decision] },
          { label: text.status, value: text[review.status] },
          {
            label: `${text.recorded} (${timezone})`,
            value: time.format(new Date(review.decidedAt)),
          },
          { label: text.actor, value: review.actorId },
          { label: text.decidingFor, value: review.decidingFor },
          { label: text.reason, value: review.reason || text.none },
          { label: text.duplicate, value: number.format(review.duplicateDigests.length) },
        ]
      : null,
    approval: [
      { label: text.status, value: text[submission.approval.status] },
      { label: text.version, value: number.format(submission.approval.version) },
      { label: text.templateRevision, value: number.format(submission.approval.templateRevision) },
    ],
    stages: submission.approval.stages.map((assignees, index) => ({
      id: String(index),
      cells: [
        number.format(index + 1),
        index < submission.approval.currentStep
          ? text.APPROVED
          : index === submission.approval.currentStep
            ? text[submission.approval.status]
            : text.none,
        assignees.join(", ") || text.noAssignees,
      ],
    })),
  };
}
