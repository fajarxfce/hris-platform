import { useController, useForm } from "react-hook-form";
import type { ExpensePolicyQuery } from "../../domain/entities/expense-policy-definition";

export function useExpensePolicyFilters(
  query: ExpensePolicyQuery,
  onQuery: (query: ExpensePolicyQuery) => void,
) {
  const form = useForm({ values: { active: query.active ?? "" } });
  const active = useController({ name: "active", control: form.control });
  return {
    active: active.field,
    submit: form.handleSubmit((fields) => onQuery({ active: fields.active || null, after: null })),
  };
}
