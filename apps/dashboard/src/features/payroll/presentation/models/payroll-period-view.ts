import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollPeriod } from "../../domain/entities/payroll-period";
import type { PayrollPeriodDetails } from "../../domain/entities/payroll-period-details";
import { payrollPeriodMessages } from "../i18n/payroll-period-messages";
import type { PayrollPeriodCreationState } from "./payroll-period-creation-state";
export function payrollPeriodProperties(period: PayrollPeriod, locale: Locale) {
  const text = payrollPeriodMessages(locale);
  return [
    { label: text.earningsMonth, value: period.earningsMonth },
    { label: text.paymentDate, value: period.plannedPaymentDate },
    { label: text.paymentMonth, value: period.plannedPaymentMonth },
    { label: text.participants, value: period.participantCount.toLocaleString(locale) },
    { label: text.status, value: text[period.status] },
    { label: text.version, value: String(period.version) },
    { label: text.timezone, value: period.timezone },
    { label: text.run, value: period.currentRunId ?? "—" },
  ];
}
export function payrollPeriodRows(items: readonly PayrollPeriod[], locale: Locale) {
  const text = payrollPeriodMessages(locale);
  return items.map((row) => ({
    id: row.id,
    actionLabel: `${row.earningsMonth} · ${text[row.status]}`,
    cells: [
      row.earningsMonth,
      row.plannedPaymentDate,
      row.participantCount.toLocaleString(locale),
      text[row.status],
      String(row.version),
    ],
  }));
}
export function payrollPeriodDetailsView(details: PayrollPeriodDetails, locale: Locale) {
  const text = payrollPeriodMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: details.period.timezone,
    });
  return {
    properties: payrollPeriodProperties(details.period, locale),
    members: details.members.items.map((row) => ({
      id: row.employeeId,
      cells: [
        row.employeeNumber,
        row.name,
        row.inputStatus === null ? text.missingInput : text[row.inputStatus],
        row.inputVersion === null ? "—" : String(row.inputVersion),
      ],
    })),
    history: details.history.items.map((row) => ({
      id: String(row.version),
      cells: [
        String(row.version),
        text[row.status],
        time.format(new Date(row.recordedAt)),
        row.reason,
        row.actorId,
      ],
    })),
  };
}
export function payrollPeriodCreationView(state: PayrollPeriodCreationState, locale: Locale) {
  const text = payrollPeriodMessages(locale),
    selected = new Set(state.selected.map((row) => row.id));
  const rows = (state.page?.items ?? []).map((row) => ({
    id: row.id,
    label: row.name,
    cells: [row.employeeNumber, row.name],
    selected: selected.has(row.id),
  }));
  const count = rows.filter((row) => row.selected).length;
  return {
    rows,
    pageChecked: (rows.length > 0 && count === rows.length ? true : count > 0 ? "mixed" : false) as
      | boolean
      | "mixed",
    selectedRows: state.selected
      .slice(state.selectedPage * 50, (state.selectedPage + 1) * 50)
      .map((row) => ({ id: row.id, actionLabel: row.name, cells: [row.employeeNumber, row.name] })),
    selectedCount: state.selected.length.toLocaleString(locale),
    selectedPages: Math.max(1, Math.ceil(state.selected.length / 50)),
    properties: state.review
      ? [
          { label: text.earningsMonth, value: state.review.earningsMonth },
          { label: text.paymentDate, value: state.review.plannedPaymentDate },
          {
            label: text.participants,
            value: state.review.employeeIds.length.toLocaleString(locale),
          },
          { label: text.reason, value: state.review.reason },
        ]
      : [],
  };
}
