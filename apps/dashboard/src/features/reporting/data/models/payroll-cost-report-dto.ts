import { z } from "zod";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const amount = z
  .string()
  .max(35)
  .regex(/^(?:0|[1-9]\d{0,31})\.\d{2}$/u);
const signed = z
  .string()
  .max(36)
  .regex(/^-?(?:0|[1-9]\d{0,31})\.\d{2}$/u);
const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
export const payrollCostReportCountsDto = z.object({
  assessments: count,
  employments: count,
  revisedAssessments: count,
  employerCost: amount,
  taxableGross: amount,
  employeeDeductions: amount,
  withheld: signed,
  takeHome: amount,
});
export const payrollCostReportDto = z.object({
  companyId: z.uuid(),
  from: month,
  until: month,
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("payroll-costs.v1"),
  currency: z.literal("IDR"),
  totals: payrollCostReportCountsDto,
  months: z
    .array(z.object({ month, counts: payrollCostReportCountsDto }))
    .min(1)
    .max(12),
});
export type PayrollCostReportDto = z.infer<typeof payrollCostReportDto>;
