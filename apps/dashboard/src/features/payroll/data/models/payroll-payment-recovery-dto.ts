import { z } from "zod";
import { payrollAmountDto } from "./payroll-payment-dto";
import { payrollPaymentProgressDto, payrollPayslipSummaryDto } from "./payroll-payslip-dto";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true });
export const payrollPaymentRecoveryDto = z.object({
  id: z.uuid(),
  assessmentId: z.uuid(),
  assessmentRevision: version,
  paymentVersion: version,
  amount: payrollAmountDto,
  currency: z.literal("IDR"),
  transactionReference: z.string().regex(/^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u),
  occurredAt: instant,
  recordedAt: instant,
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
});
export const payrollPaymentRecoveryReviewDto = z.object({
  assessmentId: z.uuid(),
  employmentId: z.uuid(),
  employeeNumber: payrollPayslipSummaryDto.shape.employeeNumber,
  employeeName: payrollPayslipSummaryDto.shape.employeeName,
  taxMonth: payrollPayslipSummaryDto.shape.taxMonth,
  publishedAt: instant,
  payments: payrollPaymentProgressDto,
  recoveries: z.array(payrollPaymentRecoveryDto).max(20),
  earliestConfirmationAt: instant,
  canConfirm: z.boolean(),
  blockers: z
    .array(z.enum(["NOT_OVERPAID", "PENDING_PAYMENT", "RECOVERY_LIMIT", "BENEFICIARY"]))
    .max(4),
});
export type PayrollPaymentRecoveryReviewDto = z.infer<typeof payrollPaymentRecoveryReviewDto>;
export type ConfirmPayrollPaymentRecoveryDto = Readonly<{
  assessmentRevision: number;
  paymentVersion: number;
  amount: string;
  transactionReference: string;
  occurredAt: string;
  reason: string;
}>;
