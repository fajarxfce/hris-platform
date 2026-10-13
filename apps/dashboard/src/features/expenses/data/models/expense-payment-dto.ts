import { z } from "zod";
import { expensePaymentBatchStatuses } from "../../domain/entities/expense-payment-batch";
import { expensePaymentActionKinds } from "../../domain/entities/expense-payment-history";
import { expensePaymentStatuses } from "../../domain/entities/expense-payment-progress";
import { expensePaymentAvailableActions } from "../../domain/entities/expense-payment-review";
import { expenseAmountDto, expenseInstantDto } from "./expense-claim-dto";
// A batch sums up to 100 claims; its total has two more integral digits than a claim.
export const expensePaymentTotalDto = z.string().regex(/^(?:0|[1-9]\d{0,13})(?:\.\d{1,2})?$/u);
const version = z.number().int().min(0).max(101);
const transactionReference = z
  .string()
  .regex(/^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u)
  .nullable();
const batchHeader = z.object({
  id: z.uuid(),
  status: z.enum(expensePaymentBatchStatuses),
  version,
  title: z.string().min(1).max(160),
  totalAmount: expensePaymentTotalDto,
  currency: z.literal("IDR"),
  itemCount: z.number().int().min(1).max(100),
  createdBy: z.uuid(),
  createdAt: expenseInstantDto,
});
export const expensePaymentItemDto = z.object({
  id: z.uuid(),
  claimId: z.uuid(),
  submissionId: z.uuid(),
  employmentId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  amount: expenseAmountDto,
  currency: z.literal("IDR"),
  destination: z.object({
    bankCode: z.string().regex(/^[A-Z0-9]{2,12}$/u),
    accountNumber: z.string().regex(/^\d{6,34}$/u),
    accountName: z.string().min(1).max(120),
  }),
  status: z.enum(expensePaymentStatuses),
  version: z.number().int().min(0).max(2),
  batchVersion: version,
  transactionReference,
  occurredAt: expenseInstantDto.nullable(),
});
export const expensePaymentBatchDto = batchHeader.extend({
  releasedBy: z.uuid().nullable(),
  releasedAt: expenseInstantDto.nullable(),
  items: z.array(expensePaymentItemDto).min(1).max(100),
});
export const expensePaymentSummaryDto = batchHeader.extend({
  pendingCount: z.number().int().min(0).max(100),
  succeededCount: z.number().int().min(0).max(100),
  failedCount: z.number().int().min(0).max(100),
});
export const expensePaymentPageDto = z.object({
  items: z.array(expensePaymentSummaryDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const expensePaymentActionDto = z.object({
  version,
  kind: z.enum(expensePaymentActionKinds),
  status: z.enum(expensePaymentBatchStatuses),
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: expenseInstantDto,
});
export const expensePaymentReconciliationDto = z.object({
  batchVersion: version,
  actorId: z.uuid(),
  recordedAt: expenseInstantDto,
  resultingStatus: z.enum(["RELEASED", "CLOSED"]),
  results: z
    .array(
      z.object({
        itemId: z.uuid(),
        status: z.enum(["SUCCEEDED", "FAILED"]),
        transactionReference,
        occurredAt: expenseInstantDto,
        reason: z.string().min(1).max(1000),
      }),
    )
    .min(1)
    .max(100),
});
export const expensePaymentReviewDto = z.object({
  batch: expensePaymentBatchDto,
  history: z.object({
    items: z.array(expensePaymentActionDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
  results: z.array(expensePaymentReconciliationDto).max(100),
  availableActions: z.array(z.enum(expensePaymentAvailableActions)).max(4),
});
export const expensePaymentWorksheetMaximumBytes = 256 * 1024;
export const expensePaymentWorksheetDto = z
  .string()
  .min(1)
  .max(expensePaymentWorksheetMaximumBytes);
export type ExpensePaymentItemDto = z.infer<typeof expensePaymentItemDto>;
export type ExpensePaymentBatchDto = z.infer<typeof expensePaymentBatchDto>;
export type ExpensePaymentPageDto = z.infer<typeof expensePaymentPageDto>;
export type ExpensePaymentReviewDto = z.infer<typeof expensePaymentReviewDto>;
