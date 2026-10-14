import type { Result } from "../../../../core/domain/result";
import type { CompensationTerms } from "../../domain/entities/compensation-terms";
import type { FixedEarning } from "../../domain/entities/fixed-earning";
import type { PayrollPayBasis } from "../../domain/entities/payroll-pay-basis";
import { holidayAllowanceRuleId, overtimeRuleId } from "../../domain/entities/payroll-pay-basis";
import type { PayrollTaxRegistration } from "../../domain/entities/payroll-tax-registration";
import type { CompensationEditorContext } from "../../domain/usecases/load-compensation-editor";
import type { CompensationFields } from "../controllers/compensation-editor-controller";

export type CompensationFormValues = {
  effectiveFrom: string;
  reason: string;
  basicSalary: string;
  fixedEarnings: FixedEarning[];
  treatment: CompensationTerms["treatment"] | "";
  residency: PayrollTaxRegistration["residency"] | "";
  ptkp: PayrollTaxRegistration["ptkp"] | "";
  residenceCountry: string;
  subjectiveFrom: string;
  subjectiveUntil: string;
  verifiedOn: string;
  verificationReference: string;
  insuranceWage: string;
  insurancePrograms: CompensationTerms["insurancePrograms"][number][];
  insuranceExemptionReason: string;
  accidentRisk: CompensationTerms["accidentRisk"] | "";
  additionalHealthDependents: string;
  additionalRetirementContribution: string;
  qualifiedDonation: string;
  otherNetDeduction: string;
  payBasisEnabled: boolean;
  proration: PayrollPayBasis["proration"] | "";
  workWeek: PayrollPayBasis["workWeek"] | "";
  shortestWorkDay: NonNullable<PayrollPayBasis["shortestWorkDay"]> | "";
  overtimeEligibility: PayrollPayBasis["overtimeEligibility"] | "";
  overtimeExemptionReference: string;
  regularNonFixedWage: string;
  serviceMonthConvention: PayrollPayBasis["serviceMonthConvention"] | "";
  rounding: PayrollPayBasis["rounding"] | "";
  reviewReference: string;
};
export function compensationFormValues(
  context: CompensationEditorContext | null,
  month: string,
): CompensationFormValues {
  const t = context?.current?.terms,
    b = t?.payBasis;
  return {
    effectiveFrom: month,
    reason: "",
    basicSalary: t?.basicSalary ?? "",
    fixedEarnings: t?.fixedEarnings.map((line) => ({ ...line })) ?? [],
    treatment: t?.treatment ?? "",
    residency: t?.tax.residency ?? "",
    ptkp: t?.tax.ptkp ?? "",
    residenceCountry: t?.tax.residenceCountry ?? "",
    subjectiveFrom: t?.tax.subjectiveFrom ?? "",
    subjectiveUntil: t?.tax.subjectiveUntil ?? "",
    verifiedOn: t?.tax.verifiedOn ?? "",
    verificationReference: t?.tax.verificationReference ?? "",
    insuranceWage: t?.insuranceWage ?? "",
    insurancePrograms: t ? [...t.insurancePrograms] : [],
    insuranceExemptionReason: t?.insuranceExemptionReason ?? "",
    accidentRisk: t?.accidentRisk ?? "",
    additionalHealthDependents: String(t?.additionalHealthDependents ?? 0),
    additionalRetirementContribution: t?.additionalRetirementContribution ?? "0",
    qualifiedDonation: t?.qualifiedDonation ?? "0",
    otherNetDeduction: t?.otherNetDeduction ?? "0",
    payBasisEnabled: b != null,
    proration: b?.proration ?? "",
    workWeek: b?.workWeek ?? "",
    shortestWorkDay: b?.shortestWorkDay ?? "",
    overtimeEligibility: b?.overtimeEligibility ?? "",
    overtimeExemptionReference: b?.overtimeExemptionReference ?? "",
    regularNonFixedWage: b?.regularNonFixedWage ?? "0",
    serviceMonthConvention: b?.serviceMonthConvention ?? "",
    rounding: b?.rounding ?? "",
    reviewReference: b?.reviewReference ?? "",
  };
}
export function compensationFields(values: CompensationFormValues): Result<CompensationFields> {
  const {
    treatment,
    residency,
    ptkp,
    accidentRisk,
    proration,
    workWeek,
    overtimeEligibility,
    serviceMonthConvention,
    rounding,
  } = values;
  const fields: Record<string, string> = {};
  if (!treatment) fields["terms.treatment"] = "required";
  if (!residency) fields["terms.tax.residency"] = "required";
  if (!ptkp) fields["terms.tax.ptkp"] = "required";
  if (!accidentRisk) fields["terms.accidentRisk"] = "required";
  let payBasis: PayrollPayBasis | null = null;
  if (values.payBasisEnabled) {
    if (!proration) fields["terms.payBasis.proration"] = "required";
    if (!workWeek) fields["terms.payBasis.workWeek"] = "required";
    if (!overtimeEligibility) fields["terms.payBasis.overtimeEligibility"] = "required";
    if (!serviceMonthConvention) fields["terms.payBasis.serviceMonthConvention"] = "required";
    if (!rounding) fields["terms.payBasis.rounding"] = "required";
    if (proration && workWeek && overtimeEligibility && serviceMonthConvention && rounding)
      payBasis = {
        overtimeRuleId,
        holidayAllowanceRuleId,
        proration,
        workWeek,
        shortestWorkDay: values.shortestWorkDay || null,
        overtimeEligibility,
        overtimeExemptionReference: values.overtimeExemptionReference || null,
        regularNonFixedWage: values.regularNonFixedWage,
        serviceMonthConvention,
        rounding,
        reviewReference: values.reviewReference,
      };
  }
  if (Object.keys(fields).length || !treatment || !residency || !ptkp || !accidentRisk)
    return { ok: false, failure: { code: "invalid_compensation", fields, parameters: {} } };
  return {
    ok: true,
    value: {
      effectiveFrom: values.effectiveFrom,
      reason: values.reason,
      terms: {
        basicSalary: values.basicSalary,
        fixedEarnings: values.fixedEarnings.map((line) => ({ ...line })),
        treatment,
        tax: {
          residency,
          ptkp,
          residenceCountry: values.residenceCountry,
          subjectiveFrom: values.subjectiveFrom || null,
          subjectiveUntil: values.subjectiveUntil || null,
          verifiedOn: values.verifiedOn,
          verificationReference: values.verificationReference,
        },
        insuranceWage: values.insuranceWage,
        insurancePrograms: [...values.insurancePrograms],
        insuranceExemptionReason: values.insuranceExemptionReason || null,
        accidentRisk,
        additionalHealthDependents: /^[0-5]$/u.test(values.additionalHealthDependents)
          ? Number(values.additionalHealthDependents)
          : Number.NaN,
        additionalRetirementContribution: values.additionalRetirementContribution,
        qualifiedDonation: values.qualifiedDonation,
        otherNetDeduction: values.otherNetDeduction,
        payBasis,
      },
    },
  };
}
