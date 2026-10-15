import type {
  PayrollAmendmentPreviewDto,
  PayrollAmendmentTermsDto,
} from "../../src/features/payroll/data/models/payroll-amendment-dto";
import type { PayrollAmendmentPreparationDto } from "../../src/features/payroll/data/models/payroll-amendment-preparation-dto";
import { compensationTerms } from "./compensations";
import { amendmentCalculation, amendmentSummary } from "./payroll-amendments";
import { calculationEmployee } from "./payroll-calculations";
import { inputTerms } from "./payroll-inputs";
import { payslipId } from "./payroll-payslips";
export function amendmentPreparation(
  multiple = false,
  revision = 0,
): PayrollAmendmentPreparationDto {
  const calculation = calculationEmployee().calculation;
  if (!calculation) throw new Error("A calculation fixture is required");
  const months = multiple
    ? [
        { index: 2, month: "2026-08" },
        { index: 1, month: "2026-09" },
        { index: 3, month: "2026-10" },
      ]
    : [{ index: 1, month: "2026-09" }];
  return {
    firstAssessmentId: payslipId(),
    sourceVersions: months.map((row) => ({ assessmentId: payslipId(row.index), revision })),
    assessments: months.map((row) => ({
      source: { assessmentId: payslipId(row.index), revision },
      employee: { ...amendmentSummary().employee },
      month: row.month,
      compensation: compensationTerms(),
      input: inputTerms(),
      calculation: structuredClone(calculation),
    })),
  };
}
export function amendmentPreview(
  preparation = amendmentPreparation(),
  terms?: PayrollAmendmentTermsDto,
): PayrollAmendmentPreviewDto {
  const calculated = amendmentCalculation().preview;
  if (!calculated) throw new Error("A preview fixture is required");
  const retained = calculated.calculations[0],
    first = preparation.assessments.find(
      (row) => row.source.assessmentId === preparation.firstAssessmentId,
    );
  if (!retained || !first) throw new Error("A monthly calculation is required");
  return {
    firstAssessmentId: preparation.firstAssessmentId,
    sourceVersions: structuredClone(preparation.sourceVersions),
    calculations: preparation.assessments
      .filter((row) => row.month >= first.month)
      .map((row) => {
        const changed = row.source.assessmentId === first.source.assessmentId,
          correction = terms?.corrections.find(
            (item) => item.assessmentId === row.source.assessmentId,
          );
        return {
          source: { ...row.source },
          employee: { ...row.employee },
          month: row.month,
          compensation: correction?.compensation ?? row.compensation,
          input: correction?.input ?? row.input,
          original: structuredClone(row.calculation),
          calculation: structuredClone(changed ? retained.calculation : row.calculation),
          difference: changed
            ? { ...retained.difference }
            : {
                taxableGross: "0.00",
                withheld: "0.00",
                takeHome: "0.00",
                employeeDeductions: "0.00",
                employerCost: "0.00",
              },
        };
      }),
    difference: { ...calculated.difference },
  };
}
