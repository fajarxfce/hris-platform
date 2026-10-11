import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import { parseAttendanceRange } from "../../domain/policies/attendance-policy";

export function useAttendanceFilters(
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
      const parsed = parseAttendanceRange(fields);
      if (parsed.ok) apply(parsed.value);
      else form.setError("root", { type: parsed.failure.code });
    }),
  };
}
