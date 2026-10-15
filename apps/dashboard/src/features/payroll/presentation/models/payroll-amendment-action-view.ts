import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentDetails } from "../../domain/entities/payroll-amendment";
import { payrollAmendmentActionMessages } from "../i18n/payroll-amendment-action-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
export function payrollAmendmentActionView(
  details: PayrollAmendmentDetails | null,
  locale: Locale,
) {
  if (!details) return [];
  const s = details.amendment.summary,
    text = payrollAmendmentMessages(locale),
    action = payrollAmendmentActionMessages(locale);
  return [
    { label: text.employee, value: s.employee.name },
    { label: text.employeeNumber, value: s.employee.employeeNumber },
    { label: text.firstMonth, value: s.firstMonth },
    { label: text.currentRevision, value: String(s.version) },
    { label: text.status, value: text.statuses[s.status] },
    { label: action.corrections, value: String(details.amendment.terms.corrections.length) },
    {
      label: action.latestAttempt,
      value: details.latestCalculation
        ? `${details.latestCalculation.number} · ${text.outcomes[details.latestCalculation.status]}`
        : "—",
    },
  ];
}
