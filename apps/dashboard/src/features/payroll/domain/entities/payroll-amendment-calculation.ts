import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { CompensationEmployee } from "./compensation-employee";
import type { CompensationTerms } from "./compensation-terms";
import type { PayrollAmendmentSource } from "./payroll-amendment";
import type { PayrollCalculationOutput } from "./payroll-calculation-output";
import type { PayrollInputTerms } from "./payroll-input-terms";
export const payrollAmendmentDifferenceFields = [
  "taxableGross",
  "withheld",
  "takeHome",
  "employeeDeductions",
  "employerCost",
] as const;
export type PayrollAmendmentDifference = Readonly<
  Record<(typeof payrollAmendmentDifferenceFields)[number], string>
>;
export type PayrollAmendmentCalculationSummary = Readonly<{
  id: string;
  amendmentId: string;
  number: number;
  draftVersion: number;
  status: "SUCCEEDED" | "FAILED";
  assessmentCount: number;
  difference: PayrollAmendmentDifference | null;
  failure: Failure | null;
  actorId: string;
  recordedAt: string;
  reason: string;
}>;
export type PayrollAmendmentCalculationRow = Readonly<{
  source: PayrollAmendmentSource;
  employee: CompensationEmployee;
  month: string;
  compensation: CompensationTerms;
  input: PayrollInputTerms;
  original: PayrollCalculationOutput;
  calculation: PayrollCalculationOutput;
  difference: PayrollAmendmentDifference;
}>;
export type PayrollAmendmentPreview = Readonly<{
  firstAssessmentId: string;
  sourceVersions: readonly PayrollAmendmentSource[];
  calculations: readonly PayrollAmendmentCalculationRow[];
  difference: PayrollAmendmentDifference;
}>;
export type PayrollAmendmentCalculation = Readonly<{
  companyId: CompanyId;
  summary: PayrollAmendmentCalculationSummary;
  preview: PayrollAmendmentPreview | null;
}>;
