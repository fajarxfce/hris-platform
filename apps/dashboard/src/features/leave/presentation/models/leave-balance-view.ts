import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { EmployeeLeaveBalances } from "../../domain/entities/employee-leave-balances";
import type { LeaveEmployeeReference } from "../../domain/entities/leave-employee-reference";
import type { LeaveLedger } from "../../domain/entities/leave-ledger";
import type { LeaveLedgerEntry } from "../../domain/entities/leave-ledger-entry";
import { leaveBalanceMessages } from "../i18n/leave-balance-messages";

export function leaveBalanceEmployeeView(
  employee: LeaveEmployeeReference,
  year: number,
  locale: Locale,
) {
  const text = leaveBalanceMessages(locale);
  return [
    { label: text.employee, value: employee.name ?? text.none },
    { label: text.number, value: employee.number ?? text.none },
    { label: text.employeeId, value: employee.id },
    { label: text.year, value: String(year) },
  ];
}
export function leaveBalancesView(page: EmployeeLeaveBalances, locale: Locale) {
  const text = leaveBalanceMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: item.typeId,
    actionLabel: item.typeName,
    cells: [
      item.typeCode,
      item.typeName,
      number.format(Number(item.balance.availableDays)),
      number.format(Number(item.balance.reservedDays)),
      number.format(Number(item.balance.consumedDays)),
      item.balance.closed ? text.closed : text.open,
    ],
  }));
}
export function leaveLedgerView(ledger: LeaveLedger, locale: Locale, timezone: string) {
  const text = leaveBalanceMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const delta = new Intl.NumberFormat(locale, { signDisplay: "exceptZero" });
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const balance = ledger.balance;
  return {
    properties: [
      { label: text.type, value: `${ledger.typeName} · ${ledger.typeCode}` },
      { label: text.available, value: number.format(Number(balance.availableDays)) },
      { label: text.reserved, value: number.format(Number(balance.reservedDays)) },
      { label: text.consumed, value: number.format(Number(balance.consumedDays)) },
      { label: text.status, value: balance.closed ? text.closed : text.open },
      { label: text.version, value: number.format(balance.version) },
      { label: text.account, value: balance.accountId ?? text.uncreated },
    ],
    entries: ledger.entries.map((entry) => ({
      id: entry.id,
      actionLabel: `${text[entry.kind]} · ${entry.id}`,
      cells: [
        date.format(new Date(entry.recordedAt)),
        text[entry.kind],
        delta.format(Number(entry.availableDeltaDays)),
        delta.format(Number(entry.reservedDeltaDays)),
        delta.format(Number(entry.consumedDeltaDays)),
      ],
    })),
  };
}
export function leaveMovementView(entry: LeaveLedgerEntry, locale: Locale, timezone: string) {
  const text = leaveBalanceMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "long",
    timeZone: timezone,
  });
  const delta = new Intl.NumberFormat(locale, { signDisplay: "exceptZero" });
  return [
    { label: text.entryId, value: entry.id },
    { label: text.kind, value: text[entry.kind] },
    { label: `${text.date} (${timezone})`, value: date.format(new Date(entry.recordedAt)) },
    { label: text.availableDelta, value: delta.format(Number(entry.availableDeltaDays)) },
    { label: text.reservedDelta, value: delta.format(Number(entry.reservedDeltaDays)) },
    { label: text.consumedDelta, value: delta.format(Number(entry.consumedDeltaDays)) },
    { label: text.actor, value: entry.actorId },
    { label: text.source, value: entry.sourceId },
    { label: text.reason, value: entry.reason || text.none },
  ];
}
