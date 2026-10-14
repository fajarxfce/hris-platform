import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { Failure } from "../../../../core/domain/result";
import type { CompensationChange } from "../entities/compensation-change";
import { accidentRisks, insurancePrograms, taxTreatments } from "../entities/compensation-terms";
import {
  earningsRounding,
  holidayAllowanceRuleId,
  overtimeEligibility,
  overtimeRuleId,
  payrollWeekdays,
  payrollWorkWeeks,
  prorationBases,
  serviceMonthConventions,
} from "../entities/payroll-pay-basis";
import { ptkpStatuses, taxResidencies } from "../entities/payroll-tax-registration";
import { isPayrollMonth, payrollAmountMinor } from "./payroll-policy-validation";

export function normalizeCompensation(input: CompensationChange): CompensationChange {
  const terms = input.terms,
    tax = terms.tax,
    basis = terms.payBasis;
  return Object.freeze({
    ...input,
    effectiveFrom: input.effectiveFrom.trim(),
    reason: input.reason.trim(),
    terms: Object.freeze({
      ...terms,
      basicSalary: terms.basicSalary.trim(),
      insuranceWage: terms.insuranceWage.trim(),
      fixedEarnings: Object.freeze(
        terms.fixedEarnings.slice(0, 21).map((line) =>
          Object.freeze({
            ...line,
            code: line.code.trim().toUpperCase(),
            name: line.name.trim(),
            amount: line.amount.trim(),
          }),
        ),
      ),
      tax: Object.freeze({
        ...tax,
        residenceCountry: tax.residenceCountry.trim().toUpperCase(),
        verificationReference: tax.verificationReference.trim(),
      }),
      insurancePrograms: Object.freeze(terms.insurancePrograms.slice(0, 6)),
      insuranceExemptionReason: terms.insuranceExemptionReason?.trim() || null,
      additionalRetirementContribution: terms.additionalRetirementContribution.trim(),
      qualifiedDonation: terms.qualifiedDonation.trim(),
      otherNetDeduction: terms.otherNetDeduction.trim(),
      payBasis: basis
        ? Object.freeze({
            ...basis,
            regularNonFixedWage: basis.regularNonFixedWage.trim(),
            reviewReference: basis.reviewReference.trim(),
            overtimeExemptionReference: basis.overtimeExemptionReference?.trim() || null,
          })
        : null,
    }),
  });
}
export function validateCompensation(input: CompensationChange): Failure | null {
  const fields: Record<string, string> = {},
    t = input.terms,
    tax = t.tax,
    basis = t.payBasis;
  if (!isPayrollMonth(input.effectiveFrom)) fields.effectiveFrom = "out_of_range";
  if (!input.reason.trim().length || input.reason.length > 1000) fields.reason = "invalid";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 999)
  )
    fields.expectedVersion = "out_of_range";
  if (!Number.isSafeInteger(input.expectedEmploymentVersion) || input.expectedEmploymentVersion < 0)
    fields.expectedEmploymentVersion = "out_of_range";
  const amounts = [
    "basicSalary",
    "insuranceWage",
    "additionalRetirementContribution",
    "qualifiedDonation",
    "otherNetDeduction",
  ] as const;
  for (const name of amounts) {
    const value = payrollAmountMinor(t[name]);
    if (value === null || ((name === "basicSalary" || name === "insuranceWage") && value === 0n))
      fields[`terms.${name}`] = "out_of_range";
  }
  if (t.fixedEarnings.length > 20) fields["terms.fixedEarnings"] = "too_many";
  if (new Set(t.fixedEarnings.map((line) => line.code)).size !== t.fixedEarnings.length)
    fields["terms.fixedEarnings"] = "duplicate";
  for (const [i, line] of t.fixedEarnings.slice(0, 20).entries()) {
    if (!/^[A-Z][A-Z0-9_]{0,31}$/u.test(line.code))
      fields[`terms.fixedEarnings[${i}].code`] = "invalid";
    if (!line.name.trim().length || line.name.length > 100)
      fields[`terms.fixedEarnings[${i}].name`] = "invalid";
    if (payrollAmountMinor(line.amount) === null)
      fields[`terms.fixedEarnings[${i}].amount`] = "out_of_range";
  }
  if (!taxTreatments.includes(t.treatment)) fields["terms.treatment"] = "invalid";
  if (!taxResidencies.includes(tax.residency)) fields["terms.tax.residency"] = "invalid";
  if (!ptkpStatuses.includes(tax.ptkp)) fields["terms.tax.ptkp"] = "invalid";
  if (
    !/^[A-Z]{2}$/u.test(tax.residenceCountry) ||
    (tax.residency === "RESIDENT" && tax.residenceCountry !== "ID") ||
    (tax.residency === "NON_RESIDENT" && tax.residenceCountry === "ID")
  )
    fields["terms.tax.residenceCountry"] = "invalid";
  if (!isPayrollEvidenceDate(tax.verifiedOn)) fields["terms.tax.verifiedOn"] = "out_of_range";
  if (!isPayrollReference(tax.verificationReference))
    fields["terms.tax.verificationReference"] = "invalid";
  if (
    tax.subjectiveFrom !== null &&
    (!isPayrollEvidenceDate(tax.subjectiveFrom) ||
      tax.subjectiveFrom.slice(0, 7) > input.effectiveFrom)
  )
    fields["terms.tax.subjectiveFrom"] = "invalid";
  if (
    tax.subjectiveUntil !== null &&
    (!isPayrollEvidenceDate(tax.subjectiveUntil) ||
      tax.subjectiveUntil < `${input.effectiveFrom}-01` ||
      (tax.subjectiveFrom !== null && tax.subjectiveUntil < tax.subjectiveFrom))
  )
    fields["terms.tax.subjectiveUntil"] = "invalid";
  if (
    tax.residency === "NON_RESIDENT" &&
    (tax.subjectiveFrom !== null || tax.subjectiveUntil !== null)
  )
    fields["terms.tax.residency"] = "invalid_subjective_period";
  if (
    t.insurancePrograms.length > 5 ||
    new Set(t.insurancePrograms).size !== t.insurancePrograms.length ||
    t.insurancePrograms.some((program) => !insurancePrograms.includes(program))
  )
    fields["terms.insurancePrograms"] = "invalid";
  if (
    (t.insurancePrograms.length !== 5 && !t.insuranceExemptionReason?.trim()) ||
    (t.insuranceExemptionReason?.length ?? 0) > 1000
  )
    fields["terms.insuranceExemptionReason"] = "required";
  if (!accidentRisks.includes(t.accidentRisk)) fields["terms.accidentRisk"] = "invalid";
  if (
    !Number.isInteger(t.additionalHealthDependents) ||
    t.additionalHealthDependents < 0 ||
    t.additionalHealthDependents > 5 ||
    (t.additionalHealthDependents > 0 && !t.insurancePrograms.includes("HEALTH"))
  )
    fields["terms.additionalHealthDependents"] = "invalid";
  if (basis) {
    if (basis.overtimeRuleId !== overtimeRuleId)
      fields["terms.payBasis.overtimeRuleId"] = "unsupported";
    if (basis.holidayAllowanceRuleId !== holidayAllowanceRuleId)
      fields["terms.payBasis.holidayAllowanceRuleId"] = "unsupported";
    if (!prorationBases.includes(basis.proration)) fields["terms.payBasis.proration"] = "invalid";
    if (!payrollWorkWeeks.includes(basis.workWeek)) fields["terms.payBasis.workWeek"] = "invalid";
    if (
      (basis.workWeek === "SIX_DAYS") !== (basis.shortestWorkDay !== null) ||
      (basis.shortestWorkDay !== null && !payrollWeekdays.includes(basis.shortestWorkDay))
    )
      fields["terms.payBasis.shortestWorkDay"] = "invalid_work_week";
    if (!overtimeEligibility.includes(basis.overtimeEligibility))
      fields["terms.payBasis.overtimeEligibility"] = "invalid";
    if (
      (basis.overtimeEligibility === "EXEMPT" && basis.overtimeExemptionReference === null) ||
      (basis.overtimeEligibility === "ELIGIBLE" && basis.overtimeExemptionReference !== null) ||
      (basis.overtimeExemptionReference !== null &&
        !isPayrollReference(basis.overtimeExemptionReference))
    )
      fields["terms.payBasis.overtimeExemptionReference"] = "invalid";
    if (payrollAmountMinor(basis.regularNonFixedWage) === null)
      fields["terms.payBasis.regularNonFixedWage"] = "out_of_range";
    if (!serviceMonthConventions.includes(basis.serviceMonthConvention))
      fields["terms.payBasis.serviceMonthConvention"] = "invalid";
    if (!earningsRounding.includes(basis.rounding)) fields["terms.payBasis.rounding"] = "invalid";
    if (!isPayrollReference(basis.reviewReference))
      fields["terms.payBasis.reviewReference"] = "invalid";
  }
  if (Object.keys(fields).length) return { code: "invalid_compensation", fields, parameters: {} };
  const cash =
    (payrollAmountMinor(t.basicSalary) ?? 0n) +
    t.fixedEarnings.reduce((total, line) => total + (payrollAmountMinor(line.amount) ?? 0n), 0n);
  const extra = basis ? (payrollAmountMinor(basis.regularNonFixedWage) ?? 0n) : 0n;
  const deductions =
    (payrollAmountMinor(t.additionalRetirementContribution) ?? 0n) +
    (payrollAmountMinor(t.qualifiedDonation) ?? 0n) +
    (payrollAmountMinor(t.otherNetDeduction) ?? 0n);
  return cash + extra > 5_000_000_000_000n || deductions > cash
    ? { code: "invalid_compensation_amount", fields: {}, parameters: {} }
    : null;
}
export const isPayrollEvidenceDate = (value: string): boolean =>
  isCalendarDate(value) && value >= "1900-01-01" && value <= "2100-12-31";
export const isPayrollReference = (value: string): boolean =>
  value.trim().length > 0 && value.length <= 200 && !/[\p{Cc}]/u.test(value);
