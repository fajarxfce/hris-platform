import { useContext, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalHistoryController } from "../controllers/approval-history-controller";
import { approvalHistoryView } from "../models/approval-history-view";
import { approvalParameters } from "../models/approval-route";
import { ApprovalHistoryPage } from "../pages/approval-history-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalHistoryScreen(props: Props) {
  const { approvalId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = approvalParameters(props.access.companyId, parameters.get("after")).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate to={`/approvals?${approvalParameters(props.access.companyId, null)}`} replace />
    );
  if (!parameters.has("company"))
    return (
      <Navigate to={`/approvals/${encodeURIComponent(approvalId)}/history?${query}`} replace />
    );
  return (
    <ApprovalHistoryBinding
      key={`${props.accountId}:${props.access.companyId}:${approvalId}`}
      {...props}
      id={approvalId}
      backTo={`/approvals/${encodeURIComponent(approvalId)}?${query}`}
    />
  );
}
function ApprovalHistoryBinding(props: Props & { id: string; backTo: string }) {
  const visible = useContext(WorkspaceVisibilityContext);
  const [foreground, setForeground] = useState(() => document.visibilityState !== "hidden");
  const controller = useMemo(
    () => new ApprovalHistoryController(props.approvals.loadHistory, props.access, props.id),
    [props.approvals.loadHistory, props.access, props.id],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const changed = () => setForeground(document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, []);
  useEffect(() => {
    if (visible && foreground) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, visible, foreground]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.history ? approvalHistoryView(state.history, props.locale, props.timezone) : null),
    [state.history, props.locale, props.timezone],
  );
  return (
    <ApprovalHistoryPage
      state={state}
      view={view}
      locale={props.locale}
      timezone={props.timezone}
      companyName={props.companyName}
      backTo={props.backTo}
      onRefresh={controller.refresh}
      onFirst={controller.first}
      onNext={controller.next}
    />
  );
}
