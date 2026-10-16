import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
export function useManagedAccountSearch(query: string, apply: (query: string) => void) {
  const values = useMemo(() => ({ query }), [query]);
  const form = useForm({ values });
  const search = useController({ name: "query", control: form.control, rules: { maxLength: 100 } });
  return {
    query: search.field,
    invalid: search.fieldState.invalid,
    submit: form.handleSubmit((fields) => apply(fields.query.trim())),
  };
}
