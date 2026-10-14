import { useController, useForm } from "react-hook-form";
import { payrollPeriodStatuses } from "../../domain/entities/payroll-period";
import type { PayrollPeriodSearch } from "../../domain/entities/payroll-period-search";
export function usePayrollPeriodFilter(
  search: PayrollPeriodSearch,
  onApply: (search: PayrollPeriodSearch) => void,
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
        status: payrollPeriodStatuses.find((status) => status === values.status) ?? null,
        after: null,
      }),
    ),
  };
}
