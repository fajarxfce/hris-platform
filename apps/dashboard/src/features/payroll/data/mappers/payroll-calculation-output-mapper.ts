import type { PayrollCalculationOutput } from "../../domain/entities/payroll-calculation-output";
import type { PayrollCalculationOutputDto } from "../models/payroll-calculation-dto";

export function toPayrollCalculationOutput(
  output: PayrollCalculationOutputDto,
): PayrollCalculationOutput {
  return Object.freeze({
    ...output,
    units: Object.freeze({
      ...output.units,
      employedDates: Object.freeze([...output.units.employedDates]),
    }),
    earnings: Object.freeze(output.earnings.map((line) => Object.freeze({ ...line }))),
    overtime: Object.freeze(
      output.overtime.map((day) =>
        Object.freeze({
          ...day,
          calculation: Object.freeze({
            ...day.calculation,
            segments: Object.freeze(
              day.calculation.segments.map((segment) => Object.freeze({ ...segment })),
            ),
          }),
        }),
      ),
    ),
    holiday: output.holiday ? Object.freeze({ ...output.holiday }) : null,
    contributions: Object.freeze(output.contributions.map((line) => Object.freeze({ ...line }))),
    tax: Object.freeze({ ...output.tax }),
  });
}
