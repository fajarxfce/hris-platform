import { useController, useForm } from "react-hook-form";
import type { LeavePolicyQuery } from "../../domain/entities/leave-policy-definition";

export function useLeavePolicyFilters(
  query: LeavePolicyQuery,
  onQuery: (query: LeavePolicyQuery) => void,
) {
  const form = useForm({ values: { active: query.active ?? "" } });
  const active = useController({ name: "active", control: form.control });
  return {
    active: active.field,
    submit: form.handleSubmit((fields) => onQuery({ active: fields.active || null, after: null })),
  };
}
