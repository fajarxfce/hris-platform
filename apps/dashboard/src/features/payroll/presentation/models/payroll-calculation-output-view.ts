import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollCalculationOutput } from "../../domain/entities/payroll-calculation-output";
import { compensationMessages } from "../i18n/compensation-messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollAmountView } from "./payroll-policy-view";

export function payrollCalculationOutputView(
  calculation: PayrollCalculationOutput | null,
  locale: Locale,
) {
  const text = payrollCalculationMessages(locale),
    compensation = compensationMessages(locale);
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
    groups,
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
  };
}
