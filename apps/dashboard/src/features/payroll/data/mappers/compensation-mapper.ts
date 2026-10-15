import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CompensationChange } from "../../domain/entities/compensation-change";
import type { CompensationEmployee } from "../../domain/entities/compensation-employee";
import type {
  CompensationEmployees,
  CompensationReview,
} from "../../domain/entities/compensation-review";
import type { CompensationTerms } from "../../domain/entities/compensation-terms";
import type { EmployeeCompensation } from "../../domain/entities/employee-compensation";
import { validateCompensation } from "../../domain/policies/compensation-validation";
import type { CompensationChangeDto } from "../models/compensation-change-dto";
import type {
  CompensationEmployeeDto,
  CompensationEmployeesDto,
  CompensationReviewDto,
  CompensationTermsDto,
  EmployeeCompensationDto,
} from "../models/compensation-dto";
export const toCompensationEmployee = (raw: CompensationEmployeeDto): CompensationEmployee =>
  Object.freeze({ ...raw, id: raw.id.toLowerCase() });
export function toEmployeeCompensation(
  raw: EmployeeCompensationDto,
  company: CompanyId,
): EmployeeCompensation {
  if (
    raw.appliedRevision > raw.version ||
    validateCompensation({
      effectiveFrom: raw.effectiveFrom,
      terms: raw.terms,
      expectedVersion: raw.version,
      expectedEmploymentVersion: 0,
      reason: "Stored compensation",
    })
  )
    throw new InvalidHttpResponseError();
  const { currency: _, ...value } = raw;
  return Object.freeze({
    ...value,
    companyId: company,
    employeeId: raw.employeeId.toLowerCase(),
    terms: toCompensationTerms(raw.terms),
  });
}
export function toCompensationEmployees(
  raw: CompensationEmployeesDto,
  after: string | null,
): CompensationEmployees {
  const items = raw.items.map(toCompensationEmployee);
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.employeeNumber)).size !== items.length ||
    items.some((item) => item.employeeNumber === after) ||
    (raw.nextCursor !== null &&
      (items.length !== 20 ||
        raw.nextCursor !== items.at(-1)?.employeeNumber ||
        raw.nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toCompensationReview(
  raw: CompensationReviewDto,
  company: CompanyId,
  employeeId: string,
  month: string,
  after: string | null,
): CompensationReview {
  const employee = toCompensationEmployee(raw.employee);
  if (raw.asOf !== month || employee.id !== employeeId) throw new InvalidHttpResponseError();
  const current = raw.current ? toEmployeeCompensation(raw.current, company) : null,
    effective = raw.effective ? toEmployeeCompensation(raw.effective, company) : null;
  if (
    current &&
    (current.employeeId !== employeeId ||
      current.appliedRevision !== current.version ||
      (after !== null && Number(after) > current.version))
  )
    throw new InvalidHttpResponseError();
  if (
    !current &&
    (effective !== null ||
      after !== null ||
      raw.history.items.length ||
      raw.history.nextCursor !== null)
  )
    throw new InvalidHttpResponseError();
  if (
    effective &&
    (effective.employeeId !== employeeId ||
      effective.version !== current?.version ||
      effective.effectiveFrom > month ||
      (effective.appliedRevision === current?.appliedRevision &&
        JSON.stringify(effective) !== JSON.stringify(current)))
  )
    throw new InvalidHttpResponseError();
  let previous = after === null ? -1 : Number(after);
  const items = raw.history.items.map((row) => {
    const compensation = toEmployeeCompensation(row.compensation, company);
    if (
      compensation.employeeId !== employeeId ||
      compensation.version !== current?.version ||
      compensation.appliedRevision !== previous + 1 ||
      !Number.isFinite(Date.parse(row.recordedAt)) ||
      (compensation.appliedRevision === current?.appliedRevision &&
        JSON.stringify(compensation) !== JSON.stringify(current)) ||
      (compensation.appliedRevision === effective?.appliedRevision &&
        JSON.stringify(compensation) !== JSON.stringify(effective))
    )
      throw new InvalidHttpResponseError();
    previous = compensation.appliedRevision;
    return Object.freeze({ ...row, compensation, actorId: row.actorId.toLowerCase() });
  });
  const next = raw.history.nextCursor;
  if (
    (next === null && previous !== (current?.version ?? -1)) ||
    (next !== null &&
      (items.length !== 20 || next !== String(previous) || previous >= (current?.version ?? -1)))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    asOf: month,
    employee,
    current,
    effective,
    canChange: raw.canChange,
    history: Object.freeze({ items: Object.freeze(items), nextCursor: next }),
  });
}
export function toCompensationChangeDto(input: CompensationChange): CompensationChangeDto {
  const t = input.terms,
    b = t.payBasis;
  return {
    effectiveFrom: input.effectiveFrom,
    expectedVersion: input.expectedVersion,
    expectedEmploymentVersion: input.expectedEmploymentVersion,
    reason: input.reason,
    terms: {
      basicSalary: t.basicSalary,
      fixedEarnings: t.fixedEarnings.map((line) => ({
        code: line.code,
        name: line.name,
        amount: line.amount,
        taxable: line.taxable,
      })),
      treatment: t.treatment,
      tax: {
        residency: t.tax.residency,
        ptkp: t.tax.ptkp,
        residenceCountry: t.tax.residenceCountry,
        subjectiveFrom: t.tax.subjectiveFrom,
        subjectiveUntil: t.tax.subjectiveUntil,
        verifiedOn: t.tax.verifiedOn,
        verificationReference: t.tax.verificationReference,
      },
      insuranceWage: t.insuranceWage,
      insurancePrograms: [...t.insurancePrograms],
      insuranceExemptionReason: t.insuranceExemptionReason,
      accidentRisk: t.accidentRisk,
      additionalHealthDependents: t.additionalHealthDependents,
      additionalRetirementContribution: t.additionalRetirementContribution,
      qualifiedDonation: t.qualifiedDonation,
      otherNetDeduction: t.otherNetDeduction,
      payBasis: b
        ? {
            overtimeRuleId: b.overtimeRuleId,
            holidayAllowanceRuleId: b.holidayAllowanceRuleId,
            proration: b.proration,
            workWeek: b.workWeek,
            shortestWorkDay: b.shortestWorkDay,
            overtimeEligibility: b.overtimeEligibility,
            overtimeExemptionReference: b.overtimeExemptionReference,
            regularNonFixedWage: b.regularNonFixedWage,
            serviceMonthConvention: b.serviceMonthConvention,
            rounding: b.rounding,
            reviewReference: b.reviewReference,
          }
        : null,
    },
  };
}

export function toCompensationTerms(raw: CompensationTermsDto): CompensationTerms {
  if (
    [raw.tax.verifiedOn, raw.tax.subjectiveFrom, raw.tax.subjectiveUntil].some(
      (date) => date !== null && !isCalendarDate(date),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    fixedEarnings: Object.freeze(raw.fixedEarnings.map((line) => Object.freeze({ ...line }))),
    tax: Object.freeze({ ...raw.tax }),
    insurancePrograms: Object.freeze([...raw.insurancePrograms].sort()),
    payBasis: raw.payBasis ? Object.freeze({ ...raw.payBasis }) : null,
  });
}
