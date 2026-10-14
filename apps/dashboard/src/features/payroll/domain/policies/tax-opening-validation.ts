import type { Failure } from "../../../../core/domain/result";
import { ptkpStatuses, taxResidencies } from "../entities/payroll-tax-registration";
import type { TaxOpeningChange, TaxOpeningVerification } from "../entities/tax-opening-change";
import type { TaxOpeningTerms } from "../entities/tax-opening-terms";
import { isPayrollReference } from "./compensation-validation";
import { payrollAmountMinor } from "./payroll-policy-validation";
export const isTaxYear = (year: string): boolean =>
  /^\d{4}$/u.test(year) && year >= "2024" && year <= "2100";
export function normalizeTaxOpeningTerms(terms: TaxOpeningTerms): TaxOpeningTerms {
  return Object.freeze({
    ...terms,
    reference: terms.reference.trim(),
    history: Object.freeze({
      ...terms.history,
      taxableGross: terms.history.taxableGross.trim(),
      retirementContributions: terms.history.retirementContributions.trim(),
      qualifiedDonations: terms.history.qualifiedDonations.trim(),
      withheld: terms.history.withheld.trim(),
      previousEmployerNet: terms.history.previousEmployerNet.trim(),
      previousEmployerWithheld: terms.history.previousEmployerWithheld.trim(),
    }),
  });
}
export function normalizeTaxOpening(input: TaxOpeningChange): TaxOpeningChange {
  return Object.freeze({
    ...input,
    terms: normalizeTaxOpeningTerms(input.terms),
    reason: input.reason.trim(),
  });
}
export function validateTaxOpeningTerms(year: string, terms: TaxOpeningTerms): Failure | null {
  const fields: Record<string, string> = {};
  if (!isTaxYear(year)) fields.year = "out_of_range";
  if (!Number.isInteger(terms.throughMonth) || terms.throughMonth < 0 || terms.throughMonth > 11)
    fields["terms.throughMonth"] = "out_of_range";
  if (!taxResidencies.includes(terms.residency)) fields["terms.residency"] = "invalid";
  if (!ptkpStatuses.includes(terms.ptkp)) fields["terms.ptkp"] = "invalid";
  if (!isPayrollReference(terms.reference)) fields["terms.reference"] = "invalid";
  const h = terms.history;
  if (
    !Number.isInteger(h.employmentMonths) ||
    h.employmentMonths < 0 ||
    h.employmentMonths > terms.throughMonth
  )
    fields["terms.history.employmentMonths"] = "out_of_range";
  const values = {
    taxableGross: payrollAmountMinor(h.taxableGross, 60_000_000_000_000n),
    retirementContributions: payrollAmountMinor(h.retirementContributions, 60_000_000_000_000n),
    qualifiedDonations: payrollAmountMinor(h.qualifiedDonations, 60_000_000_000_000n),
    withheld: payrollAmountMinor(h.withheld, 60_000_000_000_000n),
    previousEmployerNet: payrollAmountMinor(h.previousEmployerNet, 60_000_000_000_000n),
    previousEmployerWithheld: payrollAmountMinor(h.previousEmployerWithheld, 60_000_000_000_000n),
  };
  for (const [name, value] of Object.entries(values))
    if (value === null) fields[`terms.history.${name}`] = "out_of_range";
  if (Object.keys(fields).length)
    return { code: "invalid_payroll_tax_opening", fields, parameters: {} };
  // Values are validated before arithmetic; no missing declaration is interpreted as zero.
  const gross = values.taxableGross as bigint,
    retirement = values.retirementContributions as bigint,
    donations = values.qualifiedDonations as bigint;
  if (
    retirement + donations > gross ||
    gross > 5_000_000_000_000n * BigInt(h.employmentMonths) ||
    (h.employmentMonths === 0 &&
      [gross, retirement, donations, values.withheld].some((value) => value !== 0n))
  )
    fields["terms.history"] = "inconsistent";
  if (terms.throughMonth === 0 && Object.values(values).some((value) => value !== 0n))
    fields["terms.history"] = "no_previous_month";
  if (
    terms.residency === "NON_RESIDENT" &&
    (values.previousEmployerNet !== 0n || values.previousEmployerWithheld !== 0n)
  )
    fields["terms.history"] = "non_resident_reconciliation_not_applicable";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_tax_opening", fields, parameters: {} }
    : null;
}
export function validateTaxOpening(year: string, input: TaxOpeningChange): Failure | null {
  if (
    !Number.isSafeInteger(input.expectedEmploymentVersion) ||
    input.expectedEmploymentVersion < 0 ||
    (input.expectedVersion !== null &&
      (!Number.isInteger(input.expectedVersion) ||
        input.expectedVersion < 0 ||
        input.expectedVersion > 998))
  )
    return { code: "invalid_version", fields: {}, parameters: {} };
  if (!input.reason.trim() || input.reason.length > 1000)
    return { code: "invalid_payroll_tax_opening", fields: { reason: "invalid" }, parameters: {} };
  return validateTaxOpeningTerms(year, input.terms);
}
export function validateTaxOpeningVerification(
  year: string,
  input: TaxOpeningVerification,
): Failure | null {
  const fields: Record<string, string> = {};
  if (!isTaxYear(year)) fields.year = "out_of_range";
  if (
    !Number.isInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion > 998
  )
    fields.expectedVersion = "out_of_range";
  if (!input.reason.trim() || input.reason.length > 1000) fields.reason = "invalid";
  return Object.keys(fields).length
    ? { code: "invalid_payroll_tax_opening", fields, parameters: {} }
    : null;
}
