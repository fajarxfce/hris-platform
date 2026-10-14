import type { Result } from "../../../../core/domain/result";
import type { PayrollInputReview } from "../../domain/entities/payroll-input-review";
import {
  type PayrollInputTerms,
  payrollDayDispositions,
  payrollDayPortions,
  payrollHolidayKinds,
  payrollPriorHolidayPayments,
} from "../../domain/entities/payroll-input-terms";
import type { PayrollInputFields } from "../controllers/payroll-input-editor-controller";
export type PayrollInputFormValues = {
  earnings: { code: string; name: string; amount: string; taxable: boolean }[];
  deductions: { code: string; name: string; amount: string }[];
  nonCashTaxable: string;
  scheduledMonthUnits: string;
  dayResolutions: { workDate: string; portion: string; disposition: string; reference: string }[];
  holidayEnabled: boolean;
  holidayKind: string;
  holidayDate: string;
  serviceFrom: string;
  priorPayment: string;
  holidayReference: string;
  promisedAmount: string;
  reviewReference: string;
  reason: string;
};
export function payrollInputFormValues(review: PayrollInputReview | null): PayrollInputFormValues {
  const t = review?.current?.terms,
    h = t?.holidayAllowance;
  return {
    earnings: t?.earnings.map((line) => ({ ...line })) ?? [],
    deductions: t?.deductions.map((line) => ({ ...line })) ?? [],
    nonCashTaxable: t?.nonCashTaxable ?? "",
    scheduledMonthUnits: t?.scheduledMonthUnits ?? "",
    dayResolutions: t?.dayResolutions.map((day) => ({ ...day })) ?? [],
    holidayEnabled: !!h,
    holidayKind: h?.kind ?? "",
    holidayDate: h?.holidayDate ?? "",
    serviceFrom: h?.continuousServiceFrom ?? "",
    priorPayment: h?.priorPayment ?? "",
    holidayReference: h?.reviewReference ?? "",
    promisedAmount: h?.promisedAmount ?? "",
    reviewReference: t?.reviewReference ?? "",
    reason: "",
  };
}
export function payrollInputFields(values: PayrollInputFormValues): Result<PayrollInputFields> {
  const fields: Record<string, string> = {},
    days: PayrollInputTerms["dayResolutions"][number][] = [];
  for (const [index, day] of values.dayResolutions.entries()) {
    const portion = payrollDayPortions.find((value) => value === day.portion),
      disposition = payrollDayDispositions.find((value) => value === day.disposition);
    if (!portion) fields[`terms.dayResolutions[${index}].portion`] = "required";
    if (!disposition) fields[`terms.dayResolutions[${index}].disposition`] = "required";
    if (portion && disposition) days.push({ ...day, portion, disposition });
  }
  let holiday: PayrollInputTerms["holidayAllowance"] = null;
  if (values.holidayEnabled) {
    const kind = payrollHolidayKinds.find((value) => value === values.holidayKind),
      priorPayment = payrollPriorHolidayPayments.find((value) => value === values.priorPayment);
    if (!kind) fields["terms.holidayAllowance.kind"] = "required";
    if (!priorPayment) fields["terms.holidayAllowance.priorPayment"] = "required";
    if (kind && priorPayment)
      holiday = {
        kind,
        holidayDate: values.holidayDate,
        continuousServiceFrom: values.serviceFrom,
        priorPayment,
        reviewReference: values.holidayReference,
        promisedAmount: values.promisedAmount.trim() || null,
      };
  }
  if (Object.keys(fields).length)
    return { ok: false, failure: { code: "invalid_payroll_input", fields, parameters: {} } };
  return {
    ok: true,
    value: {
      terms: {
        earnings: values.earnings,
        deductions: values.deductions,
        nonCashTaxable: values.nonCashTaxable,
        scheduledMonthUnits: values.scheduledMonthUnits.trim() || null,
        dayResolutions: days,
        holidayAllowance: holiday,
        reviewReference: values.reviewReference,
      },
      reason: values.reason,
    },
  };
}
