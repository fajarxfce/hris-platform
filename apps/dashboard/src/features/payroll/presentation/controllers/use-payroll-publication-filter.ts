import { useController, useForm } from "react-hook-form";
import type { PayrollPublicationSearch } from "../../domain/entities/payroll-publication-search";
import { payrollPublicationStatuses } from "../../domain/entities/payroll-publication-search";
export function usePayrollPublicationFilter(
  search: PayrollPublicationSearch,
  onApply: (search: PayrollPublicationSearch) => void,
) {
  const form = useForm({
    values: { from: search.from, until: search.until, status: search.status ?? "" },
  });
  const from = useController({ control: form.control, name: "from" }).field,
    until = useController({ control: form.control, name: "until" }).field,
    status = useController({ control: form.control, name: "status" }).field;
  return {
    from,
    until,
    status,
    submit: form.handleSubmit((values) =>
      onApply({
        from: values.from,
        until: values.until,
        status: payrollPublicationStatuses.find((status) => status === values.status) ?? null,
        after: null,
      }),
    ),
  };
}
