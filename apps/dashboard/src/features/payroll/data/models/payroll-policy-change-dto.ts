export type PayrollPolicyChangeDto = Readonly<{
  effectiveFrom: string;
  effectiveUntil: string;
  incomeTaxRuleId: string;
  insuranceRuleId: string;
  minimumMonthlyWage: string;
  healthWageCap: string;
  pensionWageCap: string;
  contributionRounding: "HALF_UP" | "UP";
  reviewReferences: readonly string[];
  expectedVersion: number | null;
  reason: string;
}>;
