import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import type { ReportSelection } from "../../domain/entities/report-selection";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import { reportingMessages } from "../i18n/reporting-messages";
export function ReportExportSelection({
  selection,
  companies,
  locale,
}: {
  selection: ReportSelection;
  companies: readonly CompanyMembership[];
  locale: Locale;
}) {
  const text = reportExportMessages(locale),
    p = selection.parameters;
  const status = overtimeStatuses.find((value) => value === p.status);
  return (
    <AppPropertyList
      title={text.selection}
      items={[
        {
          label: reportCatalogueMessages(locale).report,
          value: reportCatalogueMessages(locale)[selection.kind].title,
        },
        {
          label: text.selection,
          value: `${p.asOf ?? `${p.from} – ${p.until}`}${status ? ` / ${reportingMessages(locale)[status]}` : ""}`,
        },
        ...(p.companies.length
          ? [
              {
                label: text.companies,
                value: p.companies
                  .map((id) => companies.find((company) => company.id === id)?.name ?? id)
                  .join(", "),
              },
            ]
          : []),
      ]}
    />
  );
}
