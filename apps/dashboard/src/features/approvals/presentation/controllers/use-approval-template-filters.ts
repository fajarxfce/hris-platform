import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { ApprovalTemplateSearch } from "../../domain/entities/approval-template";

export function useApprovalTemplateFilters(
  search: ApprovalTemplateSearch,
  onSearch: (search: ApprovalTemplateSearch) => void,
) {
  const values = useMemo(
    () => ({ kind: search.kind, asOf: search.asOf }),
    [search.kind, search.asOf],
  );
  const form = useForm({ values });
  return {
    kind: useController({ name: "kind", control: form.control }).field,
    asOf: useController({ name: "asOf", control: form.control }).field,
    submit: form.handleSubmit((input) => onSearch({ ...input, after: null })),
  };
}
