import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
export function useCompensationSearch(query: string, onSearch: (query: string) => void) {
  const values = useMemo(() => ({ query }), [query]);
  const form = useForm({ values });
  const field = useController({ name: "query", control: form.control }).field;
  return { field, submit: form.handleSubmit((values) => onSearch(values.query.trim())) };
}
