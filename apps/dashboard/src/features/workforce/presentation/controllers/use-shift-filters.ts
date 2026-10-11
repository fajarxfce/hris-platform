import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import { parseShiftSearch } from "../../domain/policies/shift-policy";

export function useShiftFilters(
  search: ShiftSearch,
  locale: Locale,
  apply: (search: ShiftSearch) => void,
) {
  const values = useMemo(() => ({ query: search.query }), [search.query]);
  const form = useForm<{ query: string }>({ values });
  const query = useController({ name: "query", control: form.control });
  return {
    query: query.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((values) => {
      const result = parseShiftSearch({ query: values.query, after: null });
      if (!result.ok) form.setError("root", { type: result.failure.code });
      else apply(result.value);
    }),
  };
}
