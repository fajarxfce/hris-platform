import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { LeaveRequestQuery, LeaveStatus } from "../../domain/entities/leave-request";

export function useLeaveFilters(
  query: LeaveRequestQuery,
  onSearch: (query: LeaveRequestQuery) => void,
) {
  const values = useMemo(() => ({ status: query.status ?? "" }), [query.status]);
  const form = useForm({ values });
  return {
    status: useController({ name: "status", control: form.control }).field,
    submit: form.handleSubmit((input) =>
      onSearch({
        employeeId: query.employeeId,
        status: (input.status || null) as LeaveStatus | null,
        after: null,
      }),
    ),
  };
}
