import { useEffect, useMemo, useSyncExternalStore } from "react";
import { useNavigate } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ReportCatalogueController } from "../controllers/report-catalogue-controller";
import { reportCatalogueRows, reportPaths } from "../models/report-catalogue-view";
import { savedReportFiltersPath } from "../models/saved-report-filter-route";
import { ReportCataloguePage } from "../pages/report-catalogue-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadCatalogue: ReportingUseCases["loadCatalogue"];
  companyName: string;
  locale: Locale;
};
export function ReportCatalogueScreen(props: Props) {
  return <ReportCatalogueBinding key={`${props.accountId}:${props.access.companyId}`} {...props} />;
}
function ReportCatalogueBinding({ access, loadCatalogue, companyName, locale }: Props) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new ReportCatalogueController(loadCatalogue, access),
    [loadCatalogue, access],
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
  const rows = useMemo(
    () => (state.report ? reportCatalogueRows(state.report, locale) : []),
    [state.report, locale],
  );
  return (
    <ReportCataloguePage
      savedFiltersTo={savedReportFiltersPath(access.companyId)}
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      onRefresh={controller.refresh}
      onOpen={(id) => {
        const report = state.report?.reports.find((entry) => entry.kind === id);
        if (report) navigate(reportPaths[report.kind]);
      }}
    />
  );
}
