import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  type ExpenseClaimStatus,
  expenseClaimStatuses,
} from "../../../expenses/domain/entities/expense-claim";
import { isExpenseReportRange } from "../../domain/policies/expense-report-policy";

export function useExpenseReportFilters(
  from: string,
  until: string,
  locale: Locale,
  apply: (from: string, until: string, status: ExpenseClaimStatus | null) => void,
  status: ExpenseClaimStatus | null = null,
) {
  const values = useMemo(() => ({ from, until, status: status ?? "" }), [from, until, status]);
  const form = useForm<{ from: string; until: string; status: string }>({ values });
  const validate = (_: string, values: { from: string; until: string }) =>
    isExpenseReportRange(values.from, values.until);
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
    rules: {
      validate: (value) =>
        value === "" || expenseClaimStatuses.includes(value as ExpenseClaimStatus),
    },
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
      apply(
        value.from,
        value.until,
        value.status === "" ? null : (value.status as ExpenseClaimStatus),
      ),
    ),
  };
}
