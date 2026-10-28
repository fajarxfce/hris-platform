import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { type LeaveStatus, leaveStatuses } from "../../../leave/domain/entities/leave-request";
import { isLeaveReportRange } from "../../domain/policies/leave-report-policy";

export function useLeaveReportFilters(
  from: string,
  until: string,
  locale: Locale,
  apply: (from: string, until: string, status: LeaveStatus | null) => void,
  status: LeaveStatus | null = null,
) {
  const values = useMemo(() => ({ from, until, status: status ?? "" }), [from, until, status]);
  const form = useForm<{ from: string; until: string; status: string }>({ values });
  const validate = (_: string, values: { from: string; until: string }) =>
    isLeaveReportRange(values.from, values.until);
  const start = useController({
    name: "from",
    control: form.control,
    rules: { required: true, validate },
  });
  const end = useController({
    name: "until",
    control: form.control,
    rules: { required: true, validate },
  });
  const selectedStatus = useController({
    name: "status",
    control: form.control,
    rules: { validate: (value) => value === "" || leaveStatuses.includes(value as LeaveStatus) },
  });
  return {
    from: start.field,
    status: selectedStatus.field,
    statusError: selectedStatus.fieldState.error
      ? failureMessage({ code: "invalid_page", fields: {}, parameters: {} }, locale)
      : undefined,
    until: end.field,
    error:
      start.fieldState.error || end.fieldState.error
        ? failureMessage(
            { code: "invalid_report_range", fields: {}, parameters: { maximumDays: "366" } },
            locale,
          )
        : undefined,
    apply: form.handleSubmit((value) =>
      apply(value.from, value.until, value.status === "" ? null : (value.status as LeaveStatus)),
    ),
  };
}
