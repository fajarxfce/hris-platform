import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { type AuditSearch, defaultAuditSearch } from "../../domain/entities/audit-search";
import { validateAuditSearch } from "../../domain/policies/audit-search-policy";
import {
  type AuditFilterValues,
  auditFiltersFromSearch,
  auditSearchFromFilters,
} from "../models/audit-filter-values";

export function useAuditFilters(
  query: AuditSearch,
  locale: Locale,
  apply: (query: AuditSearch) => void,
) {
  const values = useMemo(() => auditFiltersFromSearch(query), [query]);
  const form = useForm<AuditFilterValues>({ values });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  const actorId = useController({ name: "actorId", control: form.control });
  const resourceType = useController({ name: "resourceType", control: form.control });
  const resourceId = useController({ name: "resourceId", control: form.control });
  const action = useController({ name: "action", control: form.control });
  return {
    from: from.field,
    until: until.field,
    actorId: actorId.field,
    resourceType: resourceType.field,
    resourceId: resourceId.field,
    action: action.field,
    reset: () => {
      form.reset(auditFiltersFromSearch(defaultAuditSearch));
      apply(defaultAuditSearch);
    },
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((values) => {
      const prepared = validateAuditSearch(auditSearchFromFilters(values));
      if (prepared.ok) apply(prepared.value);
      else form.setError("root", { type: prepared.failure.code });
    }),
  };
}
