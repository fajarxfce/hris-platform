import type { Failure } from "../../../../core/domain/result";
import type { PayrollPolicyChange } from "../entities/payroll-policy-change";

export const isPayrollMonth = (month: string): boolean =>
  /^\d{4}-(?:0[1-9]|1[0-2])$/u.test(month) && month >= "2024-01" && month <= "2100-12";
export const isPolicyCursor = (after: string | null): boolean =>
  after === null || /^(?:0|[1-9]\d{0,2})$/u.test(after);

/** Exact minor units with the same monthly amount bound as the API. */
export function payrollAmountMinor(value: string): bigint | null {
  if (!/^(?:0|[1-9]\d{0,10})(?:\.\d{1,2})?$/u.test(value)) return null;
  const [whole = "0", fraction = ""] = value.split(".");
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  return minor <= 5_000_000_000_000n ? minor : null;
}

export function normalizePayrollPolicy(input: PayrollPolicyChange): PayrollPolicyChange {
  return Object.freeze({
    ...input,
    effectiveFrom: input.effectiveFrom.trim(),
    effectiveUntil: input.effectiveUntil.trim(),
    incomeTaxRuleId: input.incomeTaxRuleId.trim(),
    insuranceRuleId: input.insuranceRuleId.trim(),
    minimumMonthlyWage: input.minimumMonthlyWage.trim(),
    healthWageCap: input.healthWageCap.trim(),
    pensionWageCap: input.pensionWageCap.trim(),
    reviewReferences: Object.freeze(
      input.reviewReferences.slice(0, 11).map((reference) => reference.trim()),
    ),
    reason: input.reason.trim(),
  });
}

export function validatePayrollPolicy(input: PayrollPolicyChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isPayrollMonth(input.effectiveFrom)) fields.effectiveFrom = "out_of_range";
  if (
    !isPayrollMonth(input.effectiveUntil) ||
    input.effectiveUntil < input.effectiveFrom ||
    input.effectiveUntil.slice(0, 4) !== input.effectiveFrom.slice(0, 4)
  )
    fields.effectiveUntil = "invalid_period";
  if (!/^[A-Za-z0-9_.-]{1,100}$/u.test(input.incomeTaxRuleId))
    fields.incomeTaxRuleId = "unavailable";
  if (input.insuranceRuleId !== "ID-BPJS-PU-v1") fields.insuranceRuleId = "unavailable";
  const minimum = payrollAmountMinor(input.minimumMonthlyWage);
  const health = payrollAmountMinor(input.healthWageCap);
  const pension = payrollAmountMinor(input.pensionWageCap);
  for (const [name, value] of [
    ["minimumMonthlyWage", minimum],
    ["healthWageCap", health],
    ["pensionWageCap", pension],
  ] as const)
    if (value === null || value <= 0n) fields[name] = "out_of_range";
  if (minimum !== null && health !== null && health < minimum)
    fields.healthWageCap = "below_minimum";
  if (!["HALF_UP", "UP"].includes(input.contributionRounding))
    fields.contributionRounding = "invalid";
  if (input.reviewReferences.length < 1 || input.reviewReferences.length > 10)
    fields.reviewReferences = "invalid_count";
  for (const [index, reference] of input.reviewReferences.slice(0, 10).entries()) {
    if (
      reference.length < 9 ||
      reference.length > 500 ||
      !/^https:\/\/[^\s\p{Cc}]+$/u.test(reference)
    )
      fields[`reviewReferences[${index}]`] = "invalid";
  }
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid";
  if (
    input.expectedVersion !== null &&
    (!Number.isInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 999)
  )
    fields.expectedVersion = "out_of_range";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_policy", fields, parameters: {} }
    : null;
}
