import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";

export function usePayrollPolicyMonth(month: string, onApply: (month: string) => void) {
  const values = useMemo(() => ({ month }), [month]);
  const form = useForm({ values });
  const field = useController({ name: "month", control: form.control });
  return { field: field.field, submit: form.handleSubmit((input) => onApply(input.month)) };
}
