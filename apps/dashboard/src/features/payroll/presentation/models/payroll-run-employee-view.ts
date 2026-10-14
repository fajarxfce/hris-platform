import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollRunEmployee } from "../../domain/entities/payroll-run-employee";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollRunEmployeeView(result: PayrollRunEmployee, locale: Locale) {
  const text = payrollCalculationMessages(locale),
    compensation = compensationMessages(locale),
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
  const groups = calculation
    ? [
        {
          title: text.units,
          items: [
            { label: text.totalUnits, value: calculation.units.total },
            { label: text.payableUnits, value: calculation.units.payable },
            { label: text.unpaidUnits, value: calculation.units.unpaid },
            {
              label: text.employeeDeductions,
              value: payrollAmountView(calculation.employeeDeductions, locale),
            },
            {
              label: text.employerCost,
              value: payrollAmountView(calculation.employerCost, locale),
            },
          ],
        },
        {
          title: text.tax,
          items: [
            { label: text.rule, value: calculation.tax.ruleId },
            { label: text.category, value: calculation.tax.category ?? "—" },
            {
              label: text.rate,
              value:
                calculation.tax.effectiveRate === null
                  ? "—"
                  : new Intl.NumberFormat(locale, {
                      style: "percent",
                      maximumFractionDigits: 4,
                    }).format(Number(calculation.tax.effectiveRate)),
            },
            ...(
              [
                "taxableGross",
                "taxAllowance",
                "deductionAllowance",
                "withheld",
                "takeHome",
                "annualNet",
                "annualizedNet",
                "annualTaxable",
                "annualTax",
              ] as const
            ).flatMap((key) =>
              calculation.tax[key] === null
                ? []
                : [{ label: text[key], value: payrollAmountView(calculation.tax[key], locale) }],
            ),
          ],
        },
        ...(calculation.holiday
          ? [
              {
                title: text.holiday,
                items: [
                  { label: text.holidayDate, value: calculation.holiday.holidayDate },
                  { label: text.dueDate, value: calculation.holiday.dueDate },
                  {
                    label: text.statutory,
                    value: payrollAmountView(calculation.holiday.statutoryAmount, locale),
                  },
                  {
                    label: text.topUp,
                    value: payrollAmountView(calculation.holiday.employerTopUp, locale),
                  },
                  {
                    label: text.amount,
                    value: payrollAmountView(calculation.holiday.amount, locale),
                  },
                ],
              },
            ]
          : []),
      ]
    : [];
  return {
    title: t.employeeName,
    overview,
    references,
    groups,
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
    earnings:
      calculation?.earnings.map((line, index) => ({
        id: String(index),
        cells: [
          text.earningKinds[line.kind],
          line.code,
          line.name ?? "—",
          payrollAmountView(line.amount, locale),
          line.taxable ? text.yes : text.no,
        ],
      })) ?? [],
    contributions:
      calculation?.contributions.map((line, index) => ({
        id: String(index),
        cells: [
          Object.hasOwn(compensation, line.program)
            ? compensation[line.program as keyof typeof compensation]
            : line.program,
          payrollAmountView(line.base, locale),
          payrollAmountView(line.employeeAmount, locale),
          payrollAmountView(line.employerAmount, locale),
        ],
      })) ?? [],
    overtime:
      calculation?.overtime.map((line) => ({
        id: line.date,
        cells: [
          line.date,
          Object.hasOwn(text.overtimeDayKinds, line.calculation.dayKind)
            ? text.overtimeDayKinds[line.calculation.dayKind as keyof typeof text.overtimeDayKinds]
            : line.calculation.dayKind,
          payrollAmountView(line.calculation.displayHourlyWage, locale),
          payrollAmountView(line.calculation.amount, locale),
        ],
      })) ?? [],
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
