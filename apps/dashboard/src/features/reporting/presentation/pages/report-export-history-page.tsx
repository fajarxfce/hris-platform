import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import type { ReportExportHistoryState } from "../models/report-export-state";
export function ReportExportHistoryPage({
  state,
  rows,
  companyName,
  locale,
  onRefresh,
  onOlder,
  onOpen,
}: {
  state: ReportExportHistoryState;
  rows: readonly { id: string; cells: string[]; actionLabel: string }[];
  companyName: string;
  locale: Locale;
  onRefresh: () => void;
  onOlder: () => void;
  onOpen: (id: string) => void;
}) {
  const text = reportExportMessages(locale),
    shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            <Link to="/reports">{text.catalogue}</Link>
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {state.older ? text.newest : shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.stage === "ready" && (
        <>
          <p>{text.introduction}</p>
          {rows.length ? (
            <AppResourceTable
              title={text.history}
              minWidth={640}
              columns={[
                { id: "report", label: reportCatalogueMessages(locale).report },
                { id: "status", label: text.status },
                { id: "created", label: text.created },
                { id: "expiry", label: text.expiry },
              ]}
              rows={rows}
              action={{ label: text.review, columnId: "report", onOpen }}
            />
          ) : (
            <p>{text.empty}</p>
          )}
          {state.history?.next && <AppButton onClick={onOlder}>{text.older}</AppButton>}
        </>
      )}
    </section>
  );
}
