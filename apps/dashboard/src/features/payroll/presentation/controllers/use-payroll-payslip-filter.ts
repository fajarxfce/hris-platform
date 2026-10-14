import { useController, useForm } from "react-hook-form";
import type { PayrollPayslipSearch } from "../../domain/entities/payroll-payslip";

export function usePayrollPayslipFilter(
  search: PayrollPayslipSearch,
  onApply: (search: PayrollPayslipSearch) => void,
) {
  const form = useForm({
    values: { from: search.from, until: search.until, query: search.query, scope: search.scope },
  });
  const from = useController({ control: form.control, name: "from" }).field,
    until = useController({ control: form.control, name: "until" }).field,
    query = useController({ control: form.control, name: "query" }).field,
    scope = useController({ control: form.control, name: "scope" }).field;
  return {
    from,
    until,
    query,
    scope,
    submit: form.handleSubmit((values) =>
      onApply({
        ...values,
        query: values.query.trim(),
        scope: values.scope === "SELF" ? "SELF" : "AUTHORIZED",
        employeeId: null,
        after: null,
      }),
    ),
  };
}
