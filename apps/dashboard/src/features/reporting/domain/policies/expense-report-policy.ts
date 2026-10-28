import { expenseClaimStatuses } from "../../../expenses/domain/entities/expense-claim";
import { canBrowseCompanyExpenses } from "../../../expenses/domain/policies/expense-claim-read-policy";
import type { ExpenseReportQuery } from "../entities/expense-report-claim";

export function canReadExpenseReport(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && canBrowseCompanyExpenses(permissions);
}

export function isExpenseReportRange(from: string, until: string): boolean {
  const valid = (value: string) => {
    if (!/^(?:19\d{2}|2[01]\d{2}|2200)-\d{2}-\d{2}$/u.test(value)) return false;
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
  };
  if (!valid(from) || !valid(until)) return false;
  const days = (Date.parse(`${until}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000;
  return days >= 0 && days <= 365;
}

export function expenseReportCursorPrefix(
  company: string,
  query: Pick<ExpenseReportQuery, "from" | "until" | "status">,
): string {
  return `expenses.v1|${company}|${query.from}|${query.until}|${query.status ?? "ALL"}|`;
}

export function isExpenseReportPage(company: string, query: ExpenseReportQuery): boolean {
  if (query.status !== null && !expenseClaimStatuses.includes(query.status)) return false;
  if (query.after === null) return true;
  const prefix = expenseReportCursorPrefix(company, query);
  if (query.after.length > 200 || !query.after.startsWith(prefix)) return false;
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z\|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(
    query.after.slice(prefix.length),
  );
}
