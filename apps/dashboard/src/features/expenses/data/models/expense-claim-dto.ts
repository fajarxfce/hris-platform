import { z } from "zod";
import { expenseClaimStatuses } from "../../domain/entities/expense-claim";
import { expenseChangeKinds } from "../../domain/entities/expense-claim-history";
import { expensePaymentStatuses } from "../../domain/entities/expense-payment-progress";
export const expenseAmountDto = z.string().regex(/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/u);
export const expenseVersionDto = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const expenseInstantDto = z.iso.datetime({ offset: true }).max(40);
const draftRevision = z.number().int().min(0).max(99);
const title = z.string().min(1).max(160);
const employeeNumber = z.string().min(1).max(32);
const employeeName = z.string().min(1).max(200);
export const expenseLineDto = z.object({
  id: z.uuid(),
  categoryId: z.uuid(),
  occurredOn: z.iso.date(),
  amount: expenseAmountDto,
  description: z.string().min(1).max(500),
  costCenterId: z.uuid().nullable(),
  receiptRevisionIds: z.array(z.uuid()).max(3),
});
export const expenseDraftDto = z.object({
  revision: draftRevision,
  employeeNumber,
  employeeName,
  title,
  description: z.string().max(2000),
  totalAmount: expenseAmountDto,
  currency: z.literal("IDR"),
  lines: z.array(expenseLineDto).max(20),
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: expenseInstantDto,
});
export const expenseClaimDto = z.object({
  id: z.uuid(),
  employmentId: z.uuid(),
  createdBy: z.uuid(),
  createdAt: expenseInstantDto,
  version: expenseVersionDto,
  draftRevision,
  status: z.enum(expenseClaimStatuses),
  draft: expenseDraftDto,
  submissionCount: z.number().int().min(0).max(20),
  latestSubmissionId: z.uuid().nullable(),
});
export const expenseClaimSummaryDto = z.object({
  id: z.uuid(),
  employmentId: z.uuid(),
  employeeNumber,
  employeeName,
  title,
  totalAmount: expenseAmountDto,
  currency: z.literal("IDR"),
  createdAt: expenseInstantDto,
  status: z.enum(expenseClaimStatuses),
  version: expenseVersionDto,
});
export const expenseClaimPageDto = z.object({
  items: z.array(expenseClaimSummaryDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const expenseClaimChangeDto = z.object({
  version: expenseVersionDto,
  draftRevision,
  status: z.enum(expenseClaimStatuses),
  kind: z.enum(expenseChangeKinds),
  actorId: z.uuid(),
  reason: z.string().max(1000),
  recordedAt: expenseInstantDto,
  submissionId: z.uuid().nullable(),
});
export const expenseSubmissionSummaryDto = z.object({
  id: z.uuid(),
  number: z.number().int().min(1).max(20),
  draftRevision,
  approvalId: z.uuid(),
  title,
  totalAmount: expenseAmountDto,
  currency: z.literal("IDR"),
  submittedBy: z.uuid(),
  submittedAt: expenseInstantDto,
});
export const expensePaymentProgressDto = z.object({
  batchId: z.uuid(),
  itemId: z.uuid(),
  status: z.enum(expensePaymentStatuses),
  createdAt: expenseInstantDto,
  releasedAt: expenseInstantDto.nullable(),
  settledAt: expenseInstantDto.nullable(),
});
export const expenseClaimReviewDto = z.object({
  claim: expenseClaimDto,
  history: z.object({
    items: z.array(expenseClaimChangeDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,15})$/u)
      .nullable(),
  }),
  submissions: z.object({
    items: z.array(expenseSubmissionSummaryDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:[1-9]|1[0-9]|20)$/u)
      .nullable(),
  }),
  payments: z.array(expensePaymentProgressDto).max(20),
});
export type ExpenseClaimDto = z.infer<typeof expenseClaimDto>;
export type ExpenseClaimPageDto = z.infer<typeof expenseClaimPageDto>;
export type ExpenseClaimReviewDto = z.infer<typeof expenseClaimReviewDto>;
export type ExpenseLineDto = z.infer<typeof expenseLineDto>;
