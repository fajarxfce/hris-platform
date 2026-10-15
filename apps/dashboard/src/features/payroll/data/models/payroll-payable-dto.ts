import { z } from "zod";
import { payrollAmountDto, payrollInstantDto, payrollPaymentMonthDto } from "./payroll-payment-dto";
export const payrollPayableDto = z.object({
  assessmentId: z.uuid(),
  assessmentRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  paymentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  employmentId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  taxMonth: payrollPaymentMonthDto,
  plannedPaymentDate: z.iso.date(),
  amount: payrollAmountDto,
  currency: z.literal("IDR"),
  publishedAt: payrollInstantDto,
});
export const payrollPayablePageDto = z.object({
  items: z.array(payrollPayableDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export type PayrollPayableDto = z.infer<typeof payrollPayableDto>;
export type PayrollPayablePageDto = z.infer<typeof payrollPayablePageDto>;
