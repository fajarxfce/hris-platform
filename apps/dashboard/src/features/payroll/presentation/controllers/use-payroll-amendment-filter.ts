import { useController, useForm } from "react-hook-form";
import type { PayrollAmendmentStatus } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentSearch } from "../../domain/entities/payroll-amendment-search";
export function usePayrollAmendmentFilter(
  search: PayrollAmendmentSearch,
  onApply: (search: PayrollAmendmentSearch) => void,
) {
  const form = useForm({
    values: {
      year: Number.isFinite(search.year) ? String(search.year) : "",
      query: search.query,
      status: search.status ?? "",
    },
  });
  const year = useController({ control: form.control, name: "year" }).field,
    query = useController({ control: form.control, name: "query" }).field,
    status = useController({ control: form.control, name: "status" }).field;
  return {
    year,
    query,
    status,
    submit: form.handleSubmit((values) =>
      onApply({
        year: /^\d{4}$/u.test(values.year) ? Number(values.year) : Number.NaN,
        query: values.query.trim(),
        status: (values.status || null) as PayrollAmendmentStatus | null,
        employeeId: null,
        after: null,
      }),
    ),
  };
}
