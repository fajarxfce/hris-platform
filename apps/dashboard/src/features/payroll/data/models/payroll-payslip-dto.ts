import { z } from "zod";
import { payrollCalculationOutputDto } from "./payroll-calculation-dto";
import { payrollAmountDto } from "./payroll-payment-dto";
import { payrollSettlementDto } from "./payroll-settlement-dto";

const month = z.string().regex(/^(?:202[4-9]|20[3-9]\d|2100)-(?:0[1-9]|1[0-2])$/u);
const money = z.string().regex(/^(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u);
const signedMoney = z.string().regex(/^-?(?:0|[1-9]\d{0,14})(?:\.\d{1,2})?$/u);
const instant = z.iso.datetime({ offset: true });
export const payrollPayslipSummaryDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  taxMonth: month,
  plannedPaymentDate: z.iso.date(),
  currency: z.literal("IDR"),
  taxableGross: money,
  withheld: signedMoney,
  takeHome: money,
  publishedAt: instant,
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const payrollPayslipPageDto = z.object({
  items: z.array(payrollPayslipSummaryDto).max(50),
  nextCursor: z.string().length(44).nullable(),
});
export const payrollPayslipDto = z.object({
  summary: payrollPayslipSummaryDto,
  companyCode: z.string().min(1).max(32),
  companyName: z.string().min(1).max(200),
  calculation: payrollCalculationOutputDto,
});
export const payrollPaymentProgressDto = z.object({
  assessmentId: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  settlement: payrollSettlementDto,
  attempts: z
    .array(
      z.object({
        batchId: z.uuid(),
        itemId: z.uuid(),
        amount: payrollAmountDto,
        assessmentRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
        status: z.enum(["PREPARED", "PENDING", "SUCCEEDED", "FAILED", "CANCELLED"]),
        createdAt: instant,
        releasedAt: instant.nullable(),
        resolvedAt: instant.nullable(),
      }),
    )
    .max(20),
});
export type PayrollPayslipSummaryDto = z.infer<typeof payrollPayslipSummaryDto>;
export type PayrollPayslipDto = z.infer<typeof payrollPayslipDto>;
export type PayrollPayslipPageDto = z.infer<typeof payrollPayslipPageDto>;
export type PayrollPaymentProgressDto = z.infer<typeof payrollPaymentProgressDto>;
export type PayrollPayslipScopeDto = "AUTHORIZED" | "SELF";
export type PayrollDocumentLanguageDto = "ID" | "EN";
export type PayrollPayslipSearchDto = Readonly<{
  from: string;
  until: string;
  query: string;
  employeeId: string | null;
  scope: PayrollPayslipScopeDto;
  after: string | null;
}>;
