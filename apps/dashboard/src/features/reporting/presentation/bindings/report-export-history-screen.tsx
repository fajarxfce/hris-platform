import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { reportExportExpired } from "../../domain/policies/report-export-policy";
import type { ReportExportUseCases } from "../contracts/report-export-use-cases";
import { ReportExportHistoryController } from "../controllers/report-export-history-controller";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { reportExportMessages } from "../i18n/report-export-messages";
import { reportExportHistoryPath, reportExportPath } from "../models/report-export-route";
import { ReportExportHistoryPage } from "../pages/report-export-history-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: ReportExportUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ReportExportHistoryScreen(props: Props) {
  const [query] = useSearchParams();
  if (query.get("company") !== props.access.companyId)
    return <Navigate replace to={reportExportHistoryPath(props.access.companyId)} />;
  return <HistoryBinding key={`${props.accountId}:${props.access.companyId}`} {...props} />;
}
function HistoryBinding(props: Props) {
  const navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new ReportExportHistoryController(props.actions.readExports, props.access),
    [props.actions.readExports, props.access],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => controller.setVisible(visible), [controller, visible]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const text = reportExportMessages(props.locale),
    reports = reportCatalogueMessages(props.locale),
    date = new Intl.DateTimeFormat(props.locale, {
      timeZone: props.timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  const rows = (state.history?.items ?? []).map((item) => ({
    id: item.id,
    actionLabel: `${reports[item.selection.kind].title} ${item.id}`,
    cells: [
      `${reports[item.selection.kind].title} / ${text[item.dataset]} (${item.id.slice(0, 8)})`,
      item.retiredAt
        ? text.retired
        : reportExportExpired(item, Date.now())
          ? text.expired
          : text[item.status],
      date.format(new Date(item.createdAt)),
      date.format(new Date(item.expiresAt)),
    ],
  }));
  return (
    <ReportExportHistoryPage
      state={state}
      rows={rows}
      companyName={props.companyName}
      locale={props.locale}
      onRefresh={controller.refresh}
      onOlder={controller.older}
      onOpen={(id) => {
        if (state.history?.items.some((item) => item.id === id))
          navigate(reportExportPath(props.access.companyId, id));
      }}
    />
  );
}
