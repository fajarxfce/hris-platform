import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  AttendanceExceptionSearch,
  AttendanceExceptionSearchInput,
} from "../../domain/entities/attendance-exception-search";
import { parseAttendanceExceptionSearch } from "../../domain/policies/attendance-exception-policy";

export function useAttendanceExceptionFilters(
  search: AttendanceExceptionSearchInput,
  locale: Locale,
  apply: (search: AttendanceExceptionSearch) => void,
) {
  const values = useMemo(
    () => ({ from: search.from, until: search.until, kind: search.kind ?? "" }),
    [search.from, search.until, search.kind],
  );
  const form = useForm({ values });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  return {
    from: from.field,
    until: until.field,
    kind: kind.field,
    error: form.formState.errors.root?.type
      ? failureMessage(
          { code: String(form.formState.errors.root.type), fields: {}, parameters: {} },
          locale,
        )
      : null,
    apply: form.handleSubmit((fields) => {
      const parsed = parseAttendanceExceptionSearch({
        ...fields,
        kind: fields.kind || null,
        after: null,
      });
      if (parsed.ok) apply(parsed.value);
      else form.setError("root", { type: parsed.failure.code });
    }),
  };
}
