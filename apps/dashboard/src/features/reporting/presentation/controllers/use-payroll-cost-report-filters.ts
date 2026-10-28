import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  isPayrollCostReportRange,
  payrollCostReportRangeParameters,
} from "../../domain/policies/payroll-cost-report-policy";

export function usePayrollCostReportFilters(
  from: string,
  until: string,
  locale: Locale,
  apply: (from: string, until: string) => void,
) {
  const values = useMemo(() => ({ from, until }), [from, until]);
  const form = useForm<{ from: string; until: string }>({ values });
  const validate = (_: string, values: { from: string; until: string }) =>
    isPayrollCostReportRange(values.from, values.until);
  const start = useController({
    name: "from",
    control: form.control,
    rules: { required: true, validate },
  });
  const end = useController({
    name: "until",
    control: form.control,
    rules: { required: true, validate },
  });
  return {
    from: start.field,
    until: end.field,
    error:
      start.fieldState.error || end.fieldState.error
        ? failureMessage(
            {
              code: "invalid_report_month_range",
              fields: {},
              parameters: payrollCostReportRangeParameters,
            },
            locale,
          )
        : undefined,
    apply: form.handleSubmit((value) => apply(value.from, value.until)),
  };
}
