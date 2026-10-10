import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveRequestController } from "../controllers/leave-request-controller";
import { leaveRequestView } from "../models/leave-request-view";
import { leaveParameters, leaveQuery } from "../models/leave-route";
import { LeaveRequestPage } from "../pages/leave-request-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function LeaveRequestScreen(props: Props) {
  const { requestId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const query = leaveQuery(parameters, props.access.companyId);
  const historyAfter = parameters.get("historyAfter");
  const canonical = leaveParameters(props.access.companyId, query).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/requests?${canonical}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/leave/requests/${encodeURIComponent(requestId)}?${leaveParameters(props.access.companyId, query, historyAfter)}`}
        replace
      />
    );
  return (
    <LeaveRequestBinding
      key={`${props.accountId}:${props.access.companyId}:${requestId}`}
      {...props}
      id={requestId}
      historyAfter={historyAfter}
      backTo={`/leave/requests?${canonical}`}
      onHistory={(after) => setParameters(leaveParameters(props.access.companyId, query, after))}
      onApproval={(id) =>
        navigate(`/approvals/${encodeURIComponent(id)}?company=${props.access.companyId}`)
      }
    />
  );
}
function LeaveRequestBinding({
  access,
  leave,
  id,
  historyAfter,
  locale,
  timezone,
  companyName,
  backTo,
  onHistory,
  onApproval,
}: Props & {
  id: string;
  historyAfter: string | null;
  backTo: string;
  onHistory: (after: string | null) => void;
  onApproval: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LeaveRequestController(leave.loadRequest, access, id, historyAfter),
    [leave.loadRequest, access, id, historyAfter],
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
    () => (state.request ? leaveRequestView(state.request, locale, timezone) : null),
    [state.request, locale, timezone],
  );
  return (
    <LeaveRequestPage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      timezone={timezone}
      backTo={backTo}
      firstHistory={historyAfter === null}
      onRefresh={controller.refresh}
      onLatest={() => onHistory(null)}
      onOlder={() => {
        if (state.request?.history.nextCursor) onHistory(state.request.history.nextCursor);
      }}
      onApproval={onApproval}
    />
  );
}
