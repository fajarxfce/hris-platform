import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import type { HeadcountReport } from "../../domain/entities/headcount-report";
import { reportingMessages } from "../i18n/reporting-messages";

export function headcountView(
  report: HeadcountReport,
  companies: readonly CompanyMembership[],
  locale: Locale,
) {
  const text = reportingMessages(locale);
  const number = new Intl.NumberFormat(locale === "id" ? "id-ID" : "en-US");
  return {
    employments: number.format(report.totals.employments),
    persons: number.format(report.totals.persons),
    statuses: [
      { label: text.active, value: number.format(report.totals.active) },
      { label: text.probation, value: number.format(report.totals.probation) },
      { label: text.suspended, value: number.format(report.totals.suspended) },
    ],
    contracts: [
      { label: text.permanent, value: number.format(report.totals.permanent) },
      { label: text.fixedTerm, value: number.format(report.totals.fixedTerm) },
    ],
    companies: report.companies.map((company) => ({
      id: company.companyId,
      cells: [
        companies.find((available) => available.id === company.companyId)?.name ??
          company.companyId,
        number.format(company.counts.employments),
        number.format(company.counts.persons),
      ],
    })),
  };
}

export type HeadcountView = ReturnType<typeof headcountView>;
