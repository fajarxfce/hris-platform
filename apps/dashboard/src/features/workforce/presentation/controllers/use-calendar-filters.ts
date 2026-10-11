import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import { parseCalendarRange } from "../../domain/policies/calendar-policy";

export function useCalendarFilters(
  range: CalendarRange,
  locale: Locale,
  apply: (range: CalendarRange) => void,
) {
  const values = useMemo(() => ({ ...range }), [range]);
  const form = useForm<CalendarRange>({ values });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  return {
    from: from.field,
    until: until.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((fields) => {
      const range = parseCalendarRange(fields);
      if (range.ok) apply(range.value);
      else form.setError("root", { type: range.failure.code });
    }),
  };
}
