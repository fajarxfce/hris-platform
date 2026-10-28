import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type ReportKind, reportDefinitions } from "../../domain/entities/report-catalogue";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { SavedReportFiltersController } from "../controllers/saved-report-filters-controller";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { savedReportFilterPath, savedReportFiltersPath } from "../models/saved-report-filter-route";
import { SavedReportFiltersPage } from "../pages/saved-report-filters-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: ReportingUseCases;
  companyName: string;
  locale: Locale;
};
export function SavedReportFiltersScreen(props: Props) {
  const [query] = useSearchParams();
  if (query.get("company") !== props.access.companyId)
    return <Navigate replace to={savedReportFiltersPath(props.access.companyId)} />;
  return (
    <SavedReportFiltersBinding key={`${props.accountId}:${props.access.companyId}`} {...props} />
  );
}
function SavedReportFiltersBinding(props: Props) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new SavedReportFiltersController(props.actions.listSavedFilters, props.access),
    [props.actions.listSavedFilters, props.access],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const text = reportCatalogueMessages(props.locale);
  const rows = state.items.map((filter) => ({
    id: filter.id,
    actionLabel: filter.name,
    cells: [
      filter.name,
      text[filter.kind].title,
      filter.parameters.asOf ?? `${filter.parameters.from} – ${filter.parameters.until}`,
    ],
  }));
  return (
    <SavedReportFiltersPage
      state={state}
      rows={rows}
      companyName={props.companyName}
      locale={props.locale}
      canCreate={(Object.keys(reportDefinitions) as ReportKind[]).some((kind) =>
        canReadReportKind(props.access.permissions, kind),
      )}
      createTo={savedReportFilterPath(props.access.companyId, "new")}
      onRefresh={controller.refresh}
      onReview={(id) => {
        if (state.items.some((item) => item.id === id))
          navigate(savedReportFilterPath(props.access.companyId, id));
      }}
    />
  );
}
