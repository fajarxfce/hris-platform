import type { CompanyId } from "../../../../core/domain/identifiers";

export type PayrollCostReportCounts = Readonly<{
  assessments: number;
  employments: number;
  revisedAssessments: number;
  employerCost: string;
  taxableGross: string;
  employeeDeductions: string;
  withheld: string;
  takeHome: string;
}>;
export type PayrollCostReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  definitionVersion: "payroll-costs.v1";
  currency: "IDR";
  totals: PayrollCostReportCounts;
  months: readonly Readonly<{ month: string; counts: PayrollCostReportCounts }>[];
}>;
