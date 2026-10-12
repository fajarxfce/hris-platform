import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { DocumentEmployeeSearch } from "../../domain/entities/document-employee";
import { validDocumentEmployeeSearch } from "../../domain/policies/document-read-policy";

export function useDocumentEmployeeFilter(
  search: DocumentEmployeeSearch,
  apply: (query: string) => void,
) {
  const values = useMemo(() => ({ query: search.query }), [search.query]);
  const form = useForm({ values });
  const query = useController({ name: "query", control: form.control });
  return {
    query: query.field,
    invalid: !!form.formState.errors.query,
    apply: form.handleSubmit(({ query }) => {
      if (validDocumentEmployeeSearch({ query, after: null })) apply(query.trim());
      else form.setError("query", { type: "invalid" });
    }),
  };
}
