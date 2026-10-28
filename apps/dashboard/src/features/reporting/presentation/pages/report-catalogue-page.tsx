import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { ReportCatalogueState } from "../models/report-catalogue-state";
import type { reportCatalogueRows } from "../models/report-catalogue-view";

export function ReportCataloguePage({
  state,
  rows,
  companyName,
  locale,
  savedFiltersTo,
  exportsTo,
  onRefresh,
  onOpen,
}: {
  state: ReportCatalogueState;
  rows: ReturnType<typeof reportCatalogueRows>;
  companyName: string;
  locale: Locale;
  savedFiltersTo: string;
  exportsTo: string;
  onRefresh: () => void;
  onOpen: (id: string) => void;
}) {
  const text = reportCatalogueMessages(locale),
    shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={`${companyName} / ${shared.reports}`}
        actions={
          <>
            <Link to={savedFiltersTo}>{savedReportFilterMessages(locale).title}</Link>
            <Link to={exportsTo}>{reportExportMessages(locale).history}</Link>
            <AppButton
              icon={<ArrowClockwise20Regular />}
              onClick={onRefresh}
              disabled={state.stage === "loading"}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.report && (
        <div className="app-report-content">
          <Text className="app-muted">{rows.length ? text.introduction : text.empty}</Text>
          {rows.length > 0 && (
            <AppResourceTable
              title={text.available}
              minWidth={0}
              columns={[
                { id: "name", label: text.report },
                { id: "description", label: text.description },
              ]}
              rows={rows}
              action={{ label: text.open, onOpen, columnId: "name" }}
            />
          )}
        </div>
      )}
    </section>
  );
}
