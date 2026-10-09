import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { isHeadcountDate } from "../../domain/policies/headcount-policy";

/** Form mechanics keep partial edits separate from the applied URL filter. */
export function useHeadcountFilters(asOf: string, locale: Locale, apply: (date: string) => void) {
  const values = useMemo(() => ({ asOf: isHeadcountDate(asOf) ? asOf : "" }), [asOf]);
  const form = useForm<{ asOf: string }>({ values });
  const input = useController({
    name: "asOf",
    control: form.control,
    rules: { required: true, validate: isHeadcountDate },
  });
  return {
    date: input.field,
    error: input.fieldState.error
      ? failureMessage({ code: "invalid_report_date", fields: {}, parameters: {} }, locale)
      : undefined,
    apply: form.handleSubmit((input) => apply(input.asOf)),
  };
}
