import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { maximumSavedReportFilters } from "../../domain/policies/saved-report-filter-policy";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { SavedReportFiltersState } from "../models/saved-report-filters-state";
export function SavedReportFiltersPage({
  state,
  rows,
  companyName,
  locale,
  canCreate,
  createTo,
  onRefresh,
  onReview,
}: {
  state: SavedReportFiltersState;
  rows: readonly { id: string; actionLabel: string; cells: string[] }[];
  companyName: string;
  locale: Locale;
  canCreate: boolean;
  createTo: string;
  onRefresh: () => void;
  onReview: (id: string) => void;
}) {
  const text = savedReportFilterMessages(locale),
    shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            <Link to="/reports">{text.catalogue}</Link>
            <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
              {shared.refresh}
            </AppButton>
            {canCreate &&
              state.stage === "ready" &&
              state.items.length < maximumSavedReportFilters && (
                <Link to={createTo}>{text.create}</Link>
              )}
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.stage === "ready" && (
        <div className="app-report-content">
          <p>{text.introduction}</p>
          {state.items.length >= maximumSavedReportFilters && <p role="status">{text.limit}</p>}
          {rows.length ? (
            <AppResourceTable
              title={text.title}
              minWidth={0}
              columns={[
                { id: "name", label: text.name },
                { id: "kind", label: reportCatalogueMessages(locale).report },
                { id: "selection", label: text.selection },
              ]}
              rows={rows}
              action={{ label: text.review, columnId: "name", onOpen: onReview }}
            />
          ) : (
            <p>{text.empty}</p>
          )}
        </div>
      )}
    </section>
  );
}
