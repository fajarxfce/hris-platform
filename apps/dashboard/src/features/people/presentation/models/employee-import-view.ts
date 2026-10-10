import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { EmployeeImportPage } from "../../domain/entities/employee-import";
import type { EmployeeImportAttempts } from "../../domain/entities/employee-import-attempt";
import {
  type EmployeeImportRow,
  type EmployeeImportRows,
  employeeImportRowStatuses,
} from "../../domain/entities/employee-import-row";
import type { EmployeeImportSummary } from "../../domain/entities/employee-import-summary";
import {
  employeeImportIssueMessage,
  employeeImportMessages,
} from "../i18n/employee-import-messages";

export function employeeImportsView(page: EmployeeImportPage, locale: Locale, timezone: string) {
  const text = employeeImportMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const count = new Intl.NumberFormat(locale);
  return page.items.map((item) => ({
    id: item.id,
    actionLabel: item.fileName,
    cells: [
      item.fileName,
      text[item.status],
      count.format(item.rowCount),
      time.format(new Date(item.createdAt)),
    ],
  }));
}
export function employeeImportView(
  summary: EmployeeImportSummary,
  locale: Locale,
  timezone: string,
) {
  const text = employeeImportMessages(locale);
  const batch = summary.batch;
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  const count = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.fileName, value: batch.fileName },
      { label: text.status, value: text[batch.status] },
      { label: text.rowCount, value: count.format(batch.rowCount) },
      { label: text.version, value: count.format(batch.version) },
      { label: `${text.createdAt} (${timezone})`, value: time.format(new Date(batch.createdAt)) },
      { label: text.createdBy, value: batch.createdBy },
      { label: text.reason, value: batch.reason },
      { label: text.jobId, value: batch.jobId },
      { label: text.sourceHash, value: batch.sourceHash },
    ],
    counts: employeeImportRowStatuses.map((status) => ({
      label: text[status],
      value: count.format(summary.counts[status]),
    })),
  };
}
export function employeeImportRowsView(page: EmployeeImportRows, locale: Locale) {
  const text = employeeImportMessages(locale);
  const count = new Intl.NumberFormat(locale);
  return page.items.map((row) => ({
    id: String(row.number),
    actionLabel: `${row.number} · ${row.employeeNumber || text.none}`,
    cells: [
      count.format(row.number),
      row.employeeNumber || text.none,
      row.legalName || text.none,
      text[row.status],
      count.format(Object.keys(row.issues).length),
    ],
  }));
}
export function employeeImportRowView(row: EmployeeImportRow, locale: Locale) {
  const text = employeeImportMessages(locale);
  const fields: Readonly<Record<string, string>> = text;
  const proposed = row.proposed;
  return {
    properties: [
      { label: text.number, value: String(row.number) },
      { label: text.employeeNumber, value: row.employeeNumber || text.none },
      { label: text.legalName, value: row.legalName || text.none },
      { label: text.status, value: text[row.status] },
      { label: text.createdEmploymentId, value: row.createdEmploymentId ?? text.none },
    ],
    issues: Object.entries(row.issues).map(([field, code]) => ({
      label: fields[field] ?? `${text.field}: ${field}`,
      value: employeeImportIssueMessage(code, locale),
    })),
    proposal: proposed
      ? [
          { label: text.nationality, value: proposed.nationality || text.none },
          { label: text.email, value: proposed.email ?? text.none },
          { label: text.birthDate, value: proposed.birthDate ?? text.none },
          { label: text.startDate, value: proposed.terms.startDate },
          { label: text.endDate, value: proposed.terms.endDate ?? text.none },
          { label: text.contract, value: text[proposed.terms.contract] },
          { label: text.branchId, value: proposed.terms.branchId ?? text.none },
          { label: text.departmentId, value: proposed.terms.departmentId ?? text.none },
          { label: text.positionId, value: proposed.terms.positionId ?? text.none },
          { label: text.costCenterId, value: proposed.terms.costCenterId ?? text.none },
          { label: text.managerId, value: proposed.terms.managerId ?? text.none },
        ]
      : null,
  };
}
export function employeeImportAttemptsView(
  page: EmployeeImportAttempts,
  locale: Locale,
  timezone: string,
) {
  const text = employeeImportMessages(locale);
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return page.items.map((item) => ({
    id: item.jobId,
    cells: [text[item.phase], item.jobId, item.actorId, time.format(new Date(item.createdAt))],
  }));
}
