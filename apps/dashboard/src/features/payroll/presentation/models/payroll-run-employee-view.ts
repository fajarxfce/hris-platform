import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollRunEmployee } from "../../domain/entities/payroll-run-employee";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { payrollCalculationOutputView } from "./payroll-calculation-output-view";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollRunEmployeeView(result: PayrollRunEmployee, locale: Locale) {
  const text = payrollCalculationMessages(locale),
    input = payrollInputMessages(locale),
    { item, evidence, calculation } = result,
    t = item.target;
  const references = [
    { label: text.employmentVersion, value: String(t.employmentVersion) },
    {
      label: text.compensationRevision,
      value: t.compensationRevision === null ? "—" : String(t.compensationRevision),
    },
    { label: text.inputRevision, value: t.inputRevision === null ? "—" : String(t.inputRevision) },
    {
      label: text.openingRevision,
      value: t.taxOpeningRevision === null ? "—" : String(t.taxOpeningRevision),
    },
    { label: text.previousAssessment, value: t.previousAssessmentId ?? "—" },
    {
      label: text.previousAssessmentRevision,
      value: t.previousAssessmentId === null ? "—" : String(t.previousAssessmentRevision),
    },
  ];
  const overview = [
    { label: text.name, value: t.employeeName },
    { label: text.employeeNumber, value: t.employeeNumber },
    { label: text.status, value: item.failure ? text.failed : text.succeeded },
    ...(["taxableGross", "withheld", "takeHome"] as const).map((key) => ({
      label: text[key],
      value: item[key] === null ? "—" : payrollAmountView(item[key], locale),
    })),
  ];
  return {
    title: t.employeeName,
    overview,
    references,
    ...payrollCalculationOutputView(calculation, locale),
    source: evidence
      ? [
          { label: text.month, value: evidence.month },
          { label: text.due, value: evidence.incomeDueDate },
          { label: text.payment, value: evidence.plannedPaymentDate },
          { label: text.policyRevision, value: String(evidence.policy.appliedRevision) },
          { label: text.rule, value: evidence.policy.incomeTaxRuleId },
          { label: text.insuranceRule, value: evidence.policy.insuranceRuleId },
          { label: text.basicSalary, value: payrollAmountView(evidence.basicSalary, locale) },
          { label: text.reference, value: evidence.inputReference },
          { label: text.openingMonth, value: String(evidence.openingThroughMonth) },
          { label: text.openingReference, value: evidence.openingReference },
        ]
      : null,
    work:
      evidence?.workDays.map((day) => ({
        id: day.date,
        cells: [
          day.date,
          text.scheduleKinds[day.schedule],
          text.attendanceKinds[day.attendance],
          day.officialHoliday ? text.yes : text.no,
        ],
      })) ?? [],
    leave:
      evidence?.leaveDays.map((day, index) => ({
        id: String(index),
        cells: [
          day.date,
          Object.hasOwn(input, day.portion)
            ? input[day.portion as keyof typeof input]
            : day.portion,
          day.paid ? text.yes : text.no,
          day.requestId,
        ],
      })) ?? [],
  };
}
