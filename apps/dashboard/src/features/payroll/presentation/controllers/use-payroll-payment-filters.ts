import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  PayrollPaymentSearch,
  PayrollPaymentSearchInput,
} from "../../domain/entities/payroll-payment-search";
import { parsePayrollPaymentSearch } from "../../domain/policies/payroll-payment-read-policy";
export function usePayrollPaymentFilters(
  search: PayrollPaymentSearchInput,
  locale: Locale,
  apply: (search: PayrollPaymentSearch) => void,
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
    submit: form.handleSubmit((fields) => {
      const parsed = parsePayrollPaymentSearch({
        ...fields,
        status: fields.status || null,
        after: null,
      });
      if (parsed.ok) apply(parsed.value);
      else form.setError("root", { type: parsed.failure.code });
    }),
  };
}
