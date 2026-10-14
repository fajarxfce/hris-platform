import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";

export function useTaxOpeningYear(year: string, onApply: (year: string) => void) {
  const values = useMemo(() => ({ year }), [year]);
  const form = useForm({ values });
  const field = useController({ name: "year", control: form.control });
  return { field: field.field, submit: form.handleSubmit((input) => onApply(input.year)) };
}
