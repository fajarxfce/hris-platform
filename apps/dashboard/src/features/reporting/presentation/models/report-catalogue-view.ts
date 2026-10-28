import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { ReportCatalogue, ReportKind } from "../../domain/entities/report-catalogue";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";

export const reportPaths: Readonly<Record<ReportKind, string>> = Object.freeze({
  HEADCOUNT: "/reports/headcount",
  ATTENDANCE_EXCEPTIONS: "/reports/attendance-exceptions",
  ATTENDANCE_CLOSINGS: "/reports/attendance-closings",
  LEAVE: "/reports/leave",
  OVERTIME: "/reports/overtime",
  EXPENSES: "/reports/expenses",
  PAYROLL_COSTS: "/reports/payroll-costs",
});
export function reportCatalogueRows(value: ReportCatalogue, locale: Locale) {
  const text = reportCatalogueMessages(locale);
  return value.reports.map(({ kind }) => ({
    id: kind,
    actionLabel: text[kind].title,
    cells: [text[kind].title, text[kind].description],
  }));
}
