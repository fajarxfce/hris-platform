import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OrganizationUnitSearchInput } from "../../domain/entities/organization-unit-search";
import { parseOrganizationUnitSearch } from "../../domain/policies/organization-unit-policy";

export function useOrganizationFilters(
  search: OrganizationUnitSearchInput,
  locale: Locale,
  apply: (search: OrganizationUnitSearchInput) => void,
) {
  const values = useMemo(
    () => ({ query: search.query, kind: search.kind ?? "", active: search.active ?? "" }),
    [search.query, search.kind, search.active],
  );
  const form = useForm<{ query: string; kind: string; active: string }>({ values });
  const query = useController({ name: "query", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  const active = useController({ name: "active", control: form.control });
  return {
    query: query.field,
    kind: kind.field,
    active: active.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((values) => {
      const next = {
        query: values.query,
        kind: values.kind || null,
        active: values.active || null,
        after: null,
      };
      const result = parseOrganizationUnitSearch(next);
      if (!result.ok) form.setError("root", { type: result.failure.code });
      else apply({ ...next, query: result.value.query });
    }),
  };
}
