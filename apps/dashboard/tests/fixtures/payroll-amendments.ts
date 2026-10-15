import type {
  PayrollAmendmentCalculationDto,
  PayrollAmendmentDetailsDto,
  PayrollAmendmentDto,
  PayrollAmendmentSummaryDto,
} from "../../src/features/payroll/data/models/payroll-amendment-dto";
import { compensationActor, compensationTerms } from "./compensations";
import { calculationEmployee, calculationEmployeeId } from "./payroll-calculations";
import { inputTerms } from "./payroll-inputs";
import { payslipId } from "./payroll-payslips";
export const amendmentId = (index = 1) =>
  `fa100000-0000-4000-8000-${String(index).padStart(12, "0")}`;
export const amendmentCalculationId = "fa200000-0000-4000-8000-000000000001";
export function amendmentSummary(version = 1, index = 1): PayrollAmendmentSummaryDto {
  return {
    id: amendmentId(index),
    firstAssessmentId: payslipId(index),
    firstMonth: "2026-09",
    employee: {
      id: calculationEmployeeId(index),
      employeeNumber: `EMP-${String(index).padStart(4, "0")}`,
      name: `Amendment employee ${index}`,
      employmentVersion: 0,
    },
    version,
    status: version === 1 ? "CALCULATED" : "DRAFT",
    action: version === 1 ? "CALCULATED" : "SAVED",
    latestCalculationId: version > 0 ? amendmentCalculationId : null,
    latestReviewId: null,
    latestPublicationId: null,
    createdAt: "2026-10-01T01:00:00Z",
    actorId: compensationActor,
    recordedAt: `2026-10-01T01:${String(Math.floor(version / 60)).padStart(2, "0")}:${String(version % 60).padStart(2, "0")}Z`,
    reason: version === 0 ? "Correct the verified monthly amount" : `Retained action ${version}`,
  };
}
export function amendmentRevision(version = 1, index = 1): PayrollAmendmentDto {
  const compensation = compensationTerms();
  compensation.basicSalary = version > 1 ? "9900000.25" : "9999999.25";
  return {
    summary: amendmentSummary(version, index),
    terms: {
      sourceVersions: [{ assessmentId: payslipId(index), revision: 0 }],
      corrections: [{ assessmentId: payslipId(index), compensation, input: inputTerms() }],
    },
  };
}
export function amendmentCalculation(index = 1, failed = false): PayrollAmendmentCalculationDto {
  const original = calculationEmployee().calculation;
  if (!original) throw new Error("A calculation fixture is required");
  const calculation = structuredClone(original);
  calculation.tax.takeHome = "9999999.75";
  calculation.tax.taxableGross = "9999999.25";
  calculation.employeeDeductions = original.employeeDeductions;
  calculation.employerCost = original.employerCost;
  const difference = {
      taxableGross: "-1.00",
      withheld: "0.00",
      takeHome: "-1.00",
      employeeDeductions: "0.00",
      employerCost: "0.00",
    },
    compensation = amendmentRevision(0, index).terms.corrections[0]?.compensation;
  if (!compensation) throw new Error("A compensation fixture is required");
  return {
    summary: {
      id: amendmentCalculationId,
      amendmentId: amendmentId(index),
      number: 1,
      draftVersion: 0,
      status: failed ? "FAILED" : "SUCCEEDED",
      assessmentCount: failed ? 0 : 1,
      difference: failed ? null : { ...difference },
      failure: failed
        ? {
            kind: "VALIDATION",
            code: "payroll_compensation_required",
            fields: {},
            parameters: { taxMonth: "2026-09" },
          }
        : null,
      actorId: compensationActor,
      recordedAt: "2026-10-01T01:00:01Z",
      reason: "Retain the corrected month",
    },
    preview: failed
      ? null
      : {
          firstAssessmentId: payslipId(index),
          sourceVersions: [{ assessmentId: payslipId(index), revision: 0 }],
          calculations: [
            {
              source: { assessmentId: payslipId(index), revision: 0 },
              employee: amendmentSummary(1, index).employee,
              month: "2026-09",
              compensation,
              input: inputTerms(),
              original,
              calculation,
              difference: { ...difference },
            },
          ],
          difference: { ...difference },
        },
  };
}
export function amendmentDetails(version = 1, index = 1): PayrollAmendmentDetailsDto {
  return {
    amendment: amendmentRevision(version, index),
    history: {
      items: Array.from({ length: Math.min(version + 1, 25) }, (_, i) =>
        amendmentSummary(i, index),
      ),
      nextCursor: version >= 25 ? "24" : null,
    },
    latestCalculation: version > 0 ? amendmentCalculation(index).summary : null,
    latestReview: null,
    availableActions:
      version === 1 ? ["EDIT", "CANCEL", "SUBMIT"] : ["EDIT", "CALCULATE", "CANCEL"],
  };
}
