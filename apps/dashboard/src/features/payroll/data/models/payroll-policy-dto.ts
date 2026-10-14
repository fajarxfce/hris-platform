import { z } from "zod";

const version = z.number().int().min(0).max(999);
const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
const amount = z.string().regex(/^(?:0|[1-9]\d{0,10})(?:\.\d{1,2})?$/u);
const reference = z
  .string()
  .min(9)
  .max(500)
  .regex(/^https:\/\/[^\s\p{Cc}]+$/u);
export const payrollPolicyDto = z.object({
  version,
  appliedRevision: version,
  effectiveFrom: month,
  effectiveUntil: month,
  currency: z.literal("IDR"),
  incomeTaxRuleId: z.string().min(1).max(100),
  insuranceRuleId: z.string().min(1).max(100),
  minimumMonthlyWage: amount,
  healthWageCap: amount,
  pensionWageCap: amount,
  contributionRounding: z.enum(["HALF_UP", "UP"]),
  reviewReferences: z.array(reference).min(1).max(10),
});
export const payrollPolicyReviewDto = z.object({
  asOf: month,
  current: payrollPolicyDto.nullable(),
  effective: payrollPolicyDto.nullable(),
  history: z.object({
    items: z
      .array(
        z.object({
          policy: payrollPolicyDto,
          actorId: z.uuid(),
          reason: z.string().min(1).max(1000),
          recordedAt: z.iso.datetime({ offset: true }),
        }),
      )
      .max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,2})$/u)
      .nullable(),
  }),
});
export const payrollIncomeTaxRulesDto = z
  .array(
    z.object({
      id: z.string().min(1).max(100),
      effectiveFrom: z.string().length(10),
      effectiveUntil: z.string().length(10).nullable(),
      sources: z.array(reference).min(1).max(10),
    }),
  )
  .min(1)
  .max(50);
export const payrollPolicyReceiptDto = z.object({ id: z.uuid(), version });
export type PayrollPolicyDto = z.infer<typeof payrollPolicyDto>;
export type PayrollPolicyReviewDto = z.infer<typeof payrollPolicyReviewDto>;
export type PayrollIncomeTaxRulesDto = z.infer<typeof payrollIncomeTaxRulesDto>;
export type PayrollPolicyReceiptDto = z.infer<typeof payrollPolicyReceiptDto>;
