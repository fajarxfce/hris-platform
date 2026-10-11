import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { HolidayRange } from "../../domain/entities/holiday-range";
import { validateHolidayRange } from "../../domain/policies/holiday-policy";

export function useHolidayFilters(
  range: HolidayRange,
  locale: Locale,
  apply: (range: HolidayRange) => void,
) {
  const values = useMemo(
    () => ({ from: range.from, until: range.until }),
    [range.from, range.until],
  );
  const form = useForm<{ from: string; until: string }>({ values });
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
    apply: form.handleSubmit((values) => {
      const result = validateHolidayRange(values);
      if (!result.ok) form.setError("root", { type: result.failure.code });
      else apply(result.value);
    }),
  };
}
