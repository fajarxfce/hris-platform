import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import {
  type PayrollInputSearch,
  payrollInputEmployeeStatuses,
} from "../../domain/entities/payroll-input-review";
export function usePayrollInputFilter(
  search: PayrollInputSearch,
  onApply: (value: PayrollInputSearch) => void,
) {
  const values = useMemo(
    () => ({ month: search.month, query: search.query, status: search.status ?? "" }),
    [search.month, search.query, search.status],
  );
  const form = useForm({ values });
  const month = useController({ name: "month", control: form.control }).field;
  const query = useController({ name: "query", control: form.control }).field;
  const status = useController({ name: "status", control: form.control }).field;
  return {
    month,
    query,
    status,
    submit: form.handleSubmit((value) =>
      onApply({
        month: value.month,
        query: value.query.trim(),
        status: payrollInputEmployeeStatuses.find((status) => status === value.status) ?? null,
        after: null,
      }),
    ),
  };
}
