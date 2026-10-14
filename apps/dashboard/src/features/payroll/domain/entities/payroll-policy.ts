import type { CompanyId } from "../../../../core/domain/identifiers";

export type PayrollPolicy = Readonly<{
  companyId: CompanyId;
  version: number;
  appliedRevision: number;
  effectiveFrom: string;
  effectiveUntil: string;
  incomeTaxRuleId: string;
  insuranceRuleId: string;
  minimumMonthlyWage: string;
  healthWageCap: string;
  pensionWageCap: string;
  contributionRounding: "HALF_UP" | "UP";
  reviewReferences: readonly string[];
}>;
