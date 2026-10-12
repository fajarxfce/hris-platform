import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { isWorkPeriodMonth } from "../../domain/policies/work-period-policy";

export function useWorkPeriodMonth(month: string, locale: Locale, select: (month: string) => void) {
  const values = useMemo(() => ({ month }), [month]);
  const form = useForm({ values });
  const field = useController({ name: "month", control: form.control });
  return {
    month: field.field,
    error: form.formState.errors.month
      ? failureMessage({ code: "invalid_work_period_range", fields: {}, parameters: {} }, locale)
      : undefined,
    apply: form.handleSubmit((fields) => {
      if (isWorkPeriodMonth(fields.month)) select(fields.month);
      else form.setError("month", { type: "invalid_work_period_range" });
    }),
  };
}
