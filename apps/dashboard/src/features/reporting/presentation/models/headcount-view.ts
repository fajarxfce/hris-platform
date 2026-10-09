import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { HeadcountReport } from "../../domain/entities/headcount-report";
import { reportingMessages } from "../i18n/reporting-messages";

export function headcountView(report: HeadcountReport, locale: Locale) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale === "id" ? "id-ID" : "en-US");
  return {
    employments: number.format(report.employments),
    persons: number.format(report.persons),
    statuses: [
      { label: text.active, value: number.format(report.active) },
      { label: text.probation, value: number.format(report.probation) },
      { label: text.suspended, value: number.format(report.suspended) },
    ],
    contracts: [
      { label: text.permanent, value: number.format(report.permanent) },
      { label: text.fixedTerm, value: number.format(report.fixedTerm) },
    ],
  };
}

export type HeadcountView = ReturnType<typeof headcountView>;
