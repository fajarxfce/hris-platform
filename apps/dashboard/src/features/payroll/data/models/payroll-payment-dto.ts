import { z } from "zod";
import { payrollPaymentBatchStatuses } from "../../domain/entities/payroll-payment-batch";
import { payrollPaymentActionKinds } from "../../domain/entities/payroll-payment-history";
import { payrollPaymentAvailableActions } from "../../domain/entities/payroll-payment-review";
import { payrollPaymentStatuses } from "../../domain/entities/payroll-payment-status";
export const payrollAmountDto = z.string().regex(/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/u);
export const payrollInstantDto = z.iso.datetime({ offset: true }).max(40);
export const payrollPaymentMonthDto = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
// A batch sums up to 100 finalized assessments, retaining exact IDR amounts.
export const payrollPaymentTotalDto = z.string().regex(/^(?:0|[1-9]\d{0,13})(?:\.\d{1,2})?$/u);
export const payrollPaymentVersionDto = z.number().int().min(0).max(101);
const transactionReference = z
  .string()
  .regex(/^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u)
  .nullable();
const batchHeader = z.object({
  id: z.uuid(),
  status: z.enum(payrollPaymentBatchStatuses),
  version: payrollPaymentVersionDto,
  title: z.string().min(1).max(160),
  totalAmount: payrollPaymentTotalDto,
  currency: z.literal("IDR"),
  itemCount: z.number().int().min(1).max(100),
  createdBy: z.uuid(),
  createdAt: payrollInstantDto,
});
export const payrollPaymentItemDto = z.object({
  id: z.uuid(),
  assessmentId: z.uuid(),
  assessmentRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  observedPaymentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER).nullable(),
  employmentId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  taxMonth: payrollPaymentMonthDto,
  plannedPaymentDate: z.iso.date(),
  amount: payrollAmountDto,
  currency: z.literal("IDR"),
  destination: z.object({
    bankCode: z.string().regex(/^[A-Z0-9]{2,12}$/u),
    accountNumber: z.string().regex(/^\d{6,34}$/u),
    accountName: z.string().min(1).max(120),
  }),
  status: z.enum(payrollPaymentStatuses),
  version: z.number().int().min(0).max(2),
  batchVersion: payrollPaymentVersionDto,
  transactionReference,
  occurredAt: payrollInstantDto.nullable(),
});
export const payrollPaymentBatchDto = batchHeader.extend({
  releasedBy: z.uuid().nullable(),
  releasedAt: payrollInstantDto.nullable(),
  items: z.array(payrollPaymentItemDto).min(1).max(100),
});
export const payrollPaymentSummaryDto = batchHeader.extend({
  pendingCount: z.number().int().min(0).max(100),
  succeededCount: z.number().int().min(0).max(100),
  failedCount: z.number().int().min(0).max(100),
});
export const payrollPaymentPageDto = z.object({
  items: z.array(payrollPaymentSummaryDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const payrollPaymentActionDto = z.object({
  version: payrollPaymentVersionDto,
  kind: z.enum(payrollPaymentActionKinds),
  status: z.enum(payrollPaymentBatchStatuses),
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: payrollInstantDto,
});
export const payrollPaymentReconciliationDto = z.object({
  batchVersion: payrollPaymentVersionDto,
  actorId: z.uuid(),
  recordedAt: payrollInstantDto,
  resultingStatus: z.enum(["RELEASED", "CLOSED"]),
  results: z
    .array(
      z.object({
        itemId: z.uuid(),
        status: z.enum(["SUCCEEDED", "FAILED"]),
        transactionReference,
        occurredAt: payrollInstantDto,
        reason: z.string().min(1).max(1000),
      }),
    )
    .min(1)
    .max(100),
});
export const payrollPaymentReviewDto = z.object({
  batch: payrollPaymentBatchDto,
  history: z.object({
    items: z.array(payrollPaymentActionDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
  results: z.array(payrollPaymentReconciliationDto).max(100),
  availableActions: z.array(z.enum(payrollPaymentAvailableActions)).max(4),
});
export const payrollPaymentWorksheetMaximumBytes = 256 * 1024;
export const payrollPaymentWorksheetDto = z
  .string()
  .min(1)
  .max(payrollPaymentWorksheetMaximumBytes);
export type PayrollPaymentItemDto = z.infer<typeof payrollPaymentItemDto>;
export type PayrollPaymentBatchDto = z.infer<typeof payrollPaymentBatchDto>;
export type PayrollPaymentPageDto = z.infer<typeof payrollPaymentPageDto>;
export type PayrollPaymentReviewDto = z.infer<typeof payrollPaymentReviewDto>;
