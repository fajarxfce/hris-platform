import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import { validateEmployeeSearch } from "../../domain/policies/employee-policy";

export function useEmployeeFilters(
  search: EmployeeSearch,
  locale: Locale,
  apply: (search: EmployeeSearch) => void,
) {
  const values = useMemo(
    () => ({ asOf: isCalendarDate(search.asOf) ? search.asOf : "", query: search.query }),
    [search.asOf, search.query],
  );
  const form = useForm<{ asOf: string; query: string }>({ values });
  const date = useController({ name: "asOf", control: form.control });
  const query = useController({ name: "query", control: form.control });
  return {
    date: date.field,
    query: query.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((values) => {
      const next = { ...values, after: null };
      const failure = validateEmployeeSearch(next);
      if (failure) form.setError("root", { type: failure.code });
      else apply({ ...next, query: next.query.trim() });
    }),
  };
}
