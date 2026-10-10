import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalRequestController } from "../controllers/approval-request-controller";
import { approvalParameters } from "../models/approval-route";
import { approvalRequestView } from "../models/approval-view";
import { ApprovalRequestPage } from "../pages/approval-request-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalRequestScreen(props: Props) {
  const { approvalId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = approvalParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  const backTo = `/approvals?${query}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`/approvals/${encodeURIComponent(approvalId)}?${query}`} replace />;
  return (
    <ApprovalRequestBinding
      key={`${props.accountId}:${props.access.companyId}:${approvalId}`}
      {...props}
      id={approvalId}
      backTo={backTo}
    />
  );
}
function ApprovalRequestBinding({
  access,
  approvals,
  companyName,
  timezone,
  locale,
  id,
  backTo,
}: Props & {
  id: string;
  backTo: string;
}) {
  const controller = useMemo(
    () => new ApprovalRequestController(approvals.loadRequest, access, id),
    [approvals.loadRequest, access, id],
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
  const view = useMemo(
    () => (state.request ? approvalRequestView(state.request, locale, timezone) : null),
    [state.request, locale, timezone],
  );
  return (
    <ApprovalRequestPage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      onRefresh={controller.refresh}
    />
  );
}
