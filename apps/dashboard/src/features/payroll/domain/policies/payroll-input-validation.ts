import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type {
  PayrollInputChange,
  PayrollInputVerification,
} from "../entities/payroll-input-change";
import type { PayrollInputTerms } from "../entities/payroll-input-terms";
import {
  payrollDayDispositions,
  payrollDayPortions,
  payrollHolidayKinds,
  payrollPriorHolidayPayments,
} from "../entities/payroll-input-terms";
import { isPayrollReference } from "./compensation-validation";
import { isPayrollMonth, payrollAmountMinor } from "./payroll-policy-validation";
export function normalizePayrollInputTerms(terms: PayrollInputTerms): PayrollInputTerms {
  return Object.freeze({
    earnings: Object.freeze(
      terms.earnings.slice(0, 41).map((line) =>
        Object.freeze({
          code: line.code.trim().toUpperCase(),
          name: line.name.trim(),
          amount: line.amount.trim(),
          taxable: line.taxable,
        }),
      ),
    ),
    deductions: Object.freeze(
      terms.deductions.slice(0, 21).map((line) =>
        Object.freeze({
          code: line.code.trim().toUpperCase(),
          name: line.name.trim(),
          amount: line.amount.trim(),
        }),
      ),
    ),
    nonCashTaxable: terms.nonCashTaxable.trim(),
    scheduledMonthUnits: terms.scheduledMonthUnits?.trim() || null,
    dayResolutions: Object.freeze(
      terms.dayResolutions
        .slice(0, 63)
        .map((day) => Object.freeze({ ...day, reference: day.reference.trim() })),
    ),
    holidayAllowance: terms.holidayAllowance
      ? Object.freeze({
          ...terms.holidayAllowance,
          reviewReference: terms.holidayAllowance.reviewReference.trim(),
          promisedAmount: terms.holidayAllowance.promisedAmount?.trim() || null,
        })
      : null,
    reviewReference: terms.reviewReference.trim(),
  });
}
export function normalizePayrollInput(input: PayrollInputChange): PayrollInputChange {
  return Object.freeze({
    ...input,
    workJobId: input.workJobId.toLowerCase(),
    terms: normalizePayrollInputTerms(input.terms),
    reason: input.reason.trim(),
  });
}
export function validatePayrollInputTerms(month: string, t: PayrollInputTerms): Failure | null {
  const fields: Record<string, string> = {};
  if (!isPayrollMonth(month)) fields.earningsMonth = "out_of_range";
  if (!isPayrollReference(t.reviewReference)) fields["terms.reviewReference"] = "invalid";
  if (payrollAmountMinor(t.nonCashTaxable) === null)
    fields["terms.nonCashTaxable"] = "out_of_range";
  if (
    t.scheduledMonthUnits !== null &&
    (!/^(?:[1-9]|[12]\d|3[01])(?:\.[05])?$/u.test(t.scheduledMonthUnits) ||
      Number(t.scheduledMonthUnits) > 31)
  )
    fields["terms.scheduledMonthUnits"] = "out_of_range";
  for (const [name, lines, maximum] of [
    ["earnings", t.earnings, 40],
    ["deductions", t.deductions, 20],
  ] as const) {
    if (lines.length > maximum) fields[`terms.${name}`] = "too_many";
    if (new Set(lines.map((line) => line.code)).size !== lines.length)
      fields[`terms.${name}`] = "duplicate";
    for (const [index, line] of lines.slice(0, maximum).entries()) {
      if (!/^[A-Z][A-Z0-9_]{0,31}$/u.test(line.code))
        fields[`terms.${name}[${index}].code`] = "invalid";
      if (!line.name.trim() || line.name.length > 100 || /[\p{Cc}]/u.test(line.name))
        fields[`terms.${name}[${index}].name`] = "invalid";
      const amount = payrollAmountMinor(line.amount);
      if (amount === null || amount <= 0n)
        fields[`terms.${name}[${index}].amount`] = "positive_required";
    }
  }
  if (t.dayResolutions.length > 62) fields["terms.dayResolutions"] = "too_many";
  const slots = new Set<string>();
  for (const [index, day] of t.dayResolutions.slice(0, 62).entries()) {
    if (!isCalendarDate(day.workDate) || day.workDate.slice(0, 7) !== month)
      fields[`terms.dayResolutions[${index}].workDate`] = "outside_period";
    if (!payrollDayPortions.includes(day.portion))
      fields[`terms.dayResolutions[${index}].portion`] = "invalid";
    if (!payrollDayDispositions.includes(day.disposition))
      fields[`terms.dayResolutions[${index}].disposition`] = "invalid";
    if (!isPayrollReference(day.reference))
      fields[`terms.dayResolutions[${index}].reference`] = "invalid";
    for (const portion of day.portion === "FULL"
      ? [1, 2]
      : [day.portion === "FIRST_HALF" ? 1 : 2]) {
      const key = `${day.workDate}/${portion}`;
      if (slots.has(key)) fields["terms.dayResolutions"] = "overlap";
      slots.add(key);
    }
  }
  const holiday = t.holidayAllowance;
  if (holiday) {
    if (!payrollHolidayKinds.includes(holiday.kind))
      fields["terms.holidayAllowance.kind"] = "invalid";
    if (!payrollPriorHolidayPayments.includes(holiday.priorPayment))
      fields["terms.holidayAllowance.priorPayment"] = "invalid";
    const date = Date.parse(`${holiday.holidayDate}T00:00:00Z`);
    const start = Date.parse(`${month}-01T00:00:00Z`);
    const end = new Date(start);
    end.setUTCMonth(end.getUTCMonth() + 1);
    if (
      !isCalendarDate(holiday.holidayDate) ||
      holiday.holidayDate < "2024-01-01" ||
      holiday.holidayDate > "2100-12-31" ||
      date < start - 62 * 86_400_000 ||
      date > end.getTime() + 61 * 86_400_000
    )
      fields["terms.holidayAllowance.holidayDate"] = "out_of_range";
    if (
      !isCalendarDate(holiday.continuousServiceFrom) ||
      holiday.continuousServiceFrom < "1900-01-01" ||
      holiday.continuousServiceFrom >= holiday.holidayDate
    )
      fields["terms.holidayAllowance.continuousServiceFrom"] = "invalid";
    if (!isPayrollReference(holiday.reviewReference))
      fields["terms.holidayAllowance.reviewReference"] = "invalid";
    if (holiday.promisedAmount !== null) {
      const amount = payrollAmountMinor(holiday.promisedAmount);
      if (amount === null || amount % 100n !== 0n)
        fields["terms.holidayAllowance.promisedAmount"] = "out_of_range";
    }
  }
  if (Object.keys(fields).length) return { code: "invalid_payroll_input", fields, parameters: {} };
  const earnings = t.earnings.reduce(
    (sum, line) => sum + (payrollAmountMinor(line.amount) ?? 0n),
    payrollAmountMinor(t.nonCashTaxable) ?? 0n,
  );
  const deductions = t.deductions.reduce(
    (sum, line) => sum + (payrollAmountMinor(line.amount) ?? 0n),
    0n,
  );
  return earnings > 5_000_000_000_000n || deductions > 5_000_000_000_000n
    ? { code: "payroll_amount_limit", fields: {}, parameters: {} }
    : null;
}
export function validatePayrollInput(month: string, input: PayrollInputChange): Failure | null {
  if (
    !isUuid(input.workJobId) ||
    !Number.isSafeInteger(input.expectedWorkPeriodVersion) ||
    input.expectedWorkPeriodVersion < 0 ||
    !Number.isSafeInteger(input.expectedEmploymentVersion) ||
    input.expectedEmploymentVersion < 0 ||
    (input.expectedVersion !== null &&
      (!Number.isInteger(input.expectedVersion) ||
        input.expectedVersion < 0 ||
        input.expectedVersion > 998))
  )
    return { code: "invalid_version", fields: {}, parameters: {} };
  if (!input.reason.trim() || input.reason.length > 1000)
    return { code: "invalid_payroll_input", fields: { reason: "invalid" }, parameters: {} };
  return validatePayrollInputTerms(month, input.terms);
}
export function validatePayrollInputVerification(
  month: string,
  input: PayrollInputVerification,
): Failure | null {
  const fields: Record<string, string> = {};
  if (!isPayrollMonth(month)) fields.earningsMonth = "out_of_range";
  if (
    !Number.isInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion > 998
  )
    fields.expectedVersion = "out_of_range";
  if (!input.reason.trim() || input.reason.length > 1000) fields.reason = "invalid";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_input", fields, parameters: {} }
    : null;
}
