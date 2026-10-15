import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { PayrollAmendmentTerms } from "../../domain/entities/payroll-amendment";
import type {
  PayrollAmendmentCandidates,
  PayrollAmendmentPreparation,
  PayrollAmendmentSourceSearch,
} from "../../domain/entities/payroll-amendment-preparation";
import { normalizePayrollInputTerms } from "../../domain/policies/payroll-input-validation";
import type { PayrollAmendmentTermsDto } from "../models/payroll-amendment-dto";
import type {
  PayrollAmendmentCandidatesDto,
  PayrollAmendmentPreparationDto,
} from "../models/payroll-amendment-preparation-dto";
import {
  toCompensationEmployee,
  toCompensationTerms,
  toCompensationTermsDto,
} from "./compensation-mapper";
import { toPayrollCalculationOutput } from "./payroll-calculation-output-mapper";
export function toPayrollAmendmentCandidates(
  raw: PayrollAmendmentCandidatesDto,
  company: CompanyId,
  search: PayrollAmendmentSourceSearch,
): PayrollAmendmentCandidates {
  const items = raw.items.map((row) =>
      Object.freeze({
        ...row,
        companyId: company,
        assessmentId: row.assessmentId.toLowerCase(),
        employeeId: row.employeeId.toLowerCase(),
      }),
    ),
    nextCursor = raw.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (row, index) =>
        Number(row.taxMonth.slice(0, 4)) !== search.year ||
        (search.employeeId !== null && row.employeeId !== search.employeeId) ||
        (search.after !== null && row.assessmentId <= search.after) ||
        (index > 0 && row.assessmentId <= (items[index - 1]?.assessmentId ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.assessmentId))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
export function toPayrollAmendmentPreparation(
  raw: PayrollAmendmentPreparationDto,
  company: CompanyId,
  first: string,
): PayrollAmendmentPreparation {
  const sourceVersions = raw.sourceVersions.map((row) =>
    Object.freeze({ assessmentId: row.assessmentId.toLowerCase(), revision: row.revision }),
  );
  if (
    raw.firstAssessmentId.toLowerCase() !== first ||
    sourceVersions.length !== raw.assessments.length ||
    new Set(sourceVersions.map((row) => row.assessmentId)).size !== sourceVersions.length ||
    !sourceVersions.some((row) => row.assessmentId === first)
  )
    throw new InvalidHttpResponseError();
  const assessments = raw.assessments.map((row, index) => {
    const source = Object.freeze({
      assessmentId: row.source.assessmentId.toLowerCase(),
      revision: row.source.revision,
    });
    if (
      source.assessmentId !== sourceVersions[index]?.assessmentId ||
      source.revision !== sourceVersions[index]?.revision ||
      (index > 0 && row.month <= (raw.assessments[index - 1]?.month ?? "")) ||
      row.month.slice(0, 4) !== raw.assessments[0]?.month.slice(0, 4)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      source,
      employee: toCompensationEmployee(row.employee),
      month: row.month,
      compensation: toCompensationTerms(row.compensation),
      input: normalizePayrollInputTerms(row.input),
      calculation: toPayrollCalculationOutput(row.calculation),
    });
  });
  return Object.freeze({
    companyId: company,
    firstAssessmentId: first,
    sourceVersions: Object.freeze(sourceVersions),
    assessments: Object.freeze(assessments),
  });
}
export function toPayrollAmendmentTermsDto(terms: PayrollAmendmentTerms): PayrollAmendmentTermsDto {
  return {
    sourceVersions: terms.sourceVersions.map((row) => ({
      assessmentId: row.assessmentId,
      revision: row.revision,
    })),
    corrections: terms.corrections.map((row) => ({
      assessmentId: row.assessmentId,
      compensation: row.compensation ? toCompensationTermsDto(row.compensation) : null,
      input: row.input
        ? {
            earnings: row.input.earnings.map((line) => ({
              code: line.code,
              name: line.name,
              amount: line.amount,
              taxable: line.taxable,
            })),
            deductions: row.input.deductions.map((line) => ({
              code: line.code,
              name: line.name,
              amount: line.amount,
            })),
            nonCashTaxable: row.input.nonCashTaxable,
            scheduledMonthUnits: row.input.scheduledMonthUnits,
            dayResolutions: row.input.dayResolutions.map((day) => ({
              workDate: day.workDate,
              portion: day.portion,
              disposition: day.disposition,
              reference: day.reference,
            })),
            holidayAllowance: row.input.holidayAllowance ? { ...row.input.holidayAllowance } : null,
            reviewReference: row.input.reviewReference,
          }
        : null,
    })),
  };
}
