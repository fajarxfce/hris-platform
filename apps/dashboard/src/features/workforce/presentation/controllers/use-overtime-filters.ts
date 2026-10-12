import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OvertimeSearch, OvertimeSearchInput } from "../../domain/entities/overtime-search";
import { parseOvertimeSearch } from "../../domain/policies/overtime-read-policy";

export function useOvertimeFilters(
  search: OvertimeSearchInput,
  locale: Locale,
  apply: (search: OvertimeSearch) => void,
) {
  const values = useMemo(
    () => ({ from: search.from, until: search.until, status: search.status ?? "" }),
    [search.from, search.until, search.status],
  );
  const form = useForm({ values });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  const status = useController({ name: "status", control: form.control });
  return {
    from: from.field,
    until: until.field,
    status: status.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((fields) => {
      const parsed = parseOvertimeSearch({
        ...fields,
        employeeId: search.employeeId,
        status: fields.status || null,
        after: null,
      });
      if (parsed.ok) apply(parsed.value);
      else form.setError("root", { type: parsed.failure.code });
    }),
  };
}
