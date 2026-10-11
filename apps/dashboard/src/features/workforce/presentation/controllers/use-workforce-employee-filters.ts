import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { WorkforceEmployeeSearch } from "../../domain/entities/workforce-employee-search";
import { parseWorkforceEmployeeSearch } from "../../domain/policies/calendar-policy";

export function useWorkforceEmployeeFilters(
  search: WorkforceEmployeeSearch,
  locale: Locale,
  apply: (search: WorkforceEmployeeSearch) => void,
) {
  const values = useMemo(
    () => ({ asOf: search.asOf, query: search.query }),
    [search.asOf, search.query],
  );
  const form = useForm({ values });
  const asOf = useController({ name: "asOf", control: form.control });
  const query = useController({ name: "query", control: form.control });
  return {
    asOf: asOf.field,
    query: query.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((fields) => {
      const search = parseWorkforceEmployeeSearch({ ...fields, after: null });
      if (search.ok) apply(search.value);
      else form.setError("root", { type: search.failure.code });
    }),
  };
}
