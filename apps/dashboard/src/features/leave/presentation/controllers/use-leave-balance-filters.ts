import { useController, useForm } from "react-hook-form";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";

export function useLeaveBalanceFilters(
  query: LeaveBalanceQuery,
  onQuery: (query: LeaveBalanceQuery) => void,
) {
  const form = useForm({ values: { year: query.year } });
  const year = useController({ name: "year", control: form.control });
  return {
    year: year.field,
    submit: form.handleSubmit((fields) => onQuery({ year: fields.year, after: null })),
  };
}
