import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import {
  type PayrollAmendmentCalculation,
  type PayrollAmendmentCalculationSummary,
  type PayrollAmendmentPreview,
  payrollAmendmentDifferenceFields,
} from "../../domain/entities/payroll-amendment-calculation";
import { normalizePayrollInputTerms } from "../../domain/policies/payroll-input-validation";
import type {
  PayrollAmendmentCalculationDto,
  PayrollAmendmentCalculationSummaryDto,
  PayrollAmendmentPreviewDto,
} from "../models/payroll-amendment-dto";
import { toCompensationEmployee, toCompensationTerms } from "./compensation-mapper";
import { toPayrollCalculationFailure } from "./payroll-calculation-mapper";
import { toPayrollCalculationOutput } from "./payroll-calculation-output-mapper";
import { payrollCalculationMinor } from "./payroll-run-employee-mapper";
import { toPayrollWorkDays } from "./payroll-work-mapper";
export function toPayrollAmendmentCalculationSummary(
  raw: PayrollAmendmentCalculationSummaryDto,
  amendment: string,
): PayrollAmendmentCalculationSummary {
  const succeeded = raw.status === "SUCCEEDED";
  if (
    raw.amendmentId.toLowerCase() !== amendment ||
    succeeded !== (raw.failure === null) ||
    succeeded !== (raw.difference !== null) ||
    (succeeded ? raw.assessmentCount < 1 : raw.assessmentCount !== 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    amendmentId: amendment,
    actorId: raw.actorId.toLowerCase(),
    difference: raw.difference ? Object.freeze({ ...raw.difference }) : null,
    failure: raw.failure ? toPayrollCalculationFailure(raw.failure) : null,
  });
}
export function toPayrollAmendmentPreview(
  raw: PayrollAmendmentPreviewDto,
): PayrollAmendmentPreview {
  const firstAssessmentId = raw.firstAssessmentId.toLowerCase(),
    sources = raw.sourceVersions.map((row) =>
      Object.freeze({ ...row, assessmentId: row.assessmentId.toLowerCase() }),
    );
  if (
    new Set(sources.map((row) => row.assessmentId)).size !== sources.length ||
    raw.calculations[0]?.source.assessmentId.toLowerCase() !== firstAssessmentId
  )
    throw new InvalidHttpResponseError();
  const rows = raw.calculations.map((row, index) => {
    const source = Object.freeze({
      ...row.source,
      assessmentId: row.source.assessmentId.toLowerCase(),
    });
    if (
      !sources.some(
        (expected) =>
          expected.assessmentId === source.assessmentId && expected.revision === source.revision,
      ) ||
      (index > 0 && row.month <= (raw.calculations[index - 1]?.month ?? "")) ||
      row.month.slice(0, 4) !== raw.calculations[0]?.month.slice(0, 4) ||
      (row.workAdjustmentId !== null && row.workDays === null)
    )
      throw new InvalidHttpResponseError();
    for (const key of payrollAmendmentDifferenceFields) {
      const previous =
        key === "employeeDeductions" || key === "employerCost"
          ? row.original[key]
          : row.original.tax[key];
      const next =
        key === "employeeDeductions" || key === "employerCost"
          ? row.calculation[key]
          : row.calculation.tax[key];
      if (
        payrollCalculationMinor(next) - payrollCalculationMinor(previous) !==
        payrollCalculationMinor(row.difference[key])
      )
        throw new InvalidHttpResponseError();
    }
    return Object.freeze({
      source,
      employee: toCompensationEmployee(row.employee),
      month: row.month,
      compensation: toCompensationTerms(row.compensation),
      input: normalizePayrollInputTerms(row.input),
      workAdjustmentId: row.workAdjustmentId?.toLowerCase() ?? null,
      workDays: row.workDays ? toPayrollWorkDays(row.workDays, row.month) : null,
      original: toPayrollCalculationOutput(row.original),
      calculation: toPayrollCalculationOutput(row.calculation),
      difference: Object.freeze({ ...row.difference }),
    });
  });
  if (
    new Set(rows.map((row) => row.source.assessmentId)).size !== rows.length ||
    payrollAmendmentDifferenceFields.some(
      (key) =>
        rows.reduce((total, row) => total + payrollCalculationMinor(row.difference[key]), 0n) !==
        payrollCalculationMinor(raw.difference[key]),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    firstAssessmentId,
    sourceVersions: Object.freeze(sources),
    calculations: Object.freeze(rows),
    difference: Object.freeze({ ...raw.difference }),
  });
}
export function toPayrollAmendmentCalculation(
  raw: PayrollAmendmentCalculationDto,
  company: CompanyId,
  amendment: string,
  calculation: string,
): PayrollAmendmentCalculation {
  const summary = toPayrollAmendmentCalculationSummary(raw.summary, amendment),
    preview = raw.preview ? toPayrollAmendmentPreview(raw.preview) : null;
  if (
    summary.id !== calculation ||
    (summary.status === "SUCCEEDED") !== (preview !== null) ||
    (preview &&
      (preview.calculations.length !== summary.assessmentCount ||
        payrollAmendmentDifferenceFields.some(
          (key) =>
            payrollCalculationMinor(preview.difference[key]) !==
            payrollCalculationMinor(summary.difference?.[key] ?? "0"),
        )))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ companyId: company, summary, preview });
}
