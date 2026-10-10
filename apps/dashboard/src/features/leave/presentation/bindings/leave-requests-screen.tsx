import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestQuery } from "../../domain/entities/leave-request";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveRequestsController } from "../controllers/leave-requests-controller";
import { useLeaveFilters } from "../controllers/use-leave-filters";
import { leaveRequestsView } from "../models/leave-request-view";
import { leaveParameters, leaveQuery } from "../models/leave-route";
import { LeaveRequestsPage } from "../pages/leave-requests-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  locale: Locale;
};
export function LeaveRequestsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const query = useMemo(
    () => leaveQuery(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  const canonical = leaveParameters(props.access.companyId, query).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/requests?${canonical}`} replace />;
  return (
    <LeaveRequestsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQuery={(value) => setParameters(leaveParameters(props.access.companyId, value))}
      onOpen={(id) => navigate(`/leave/requests/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function LeaveRequestsBinding({
  access,
  leave,
  companyName,
  locale,
  query,
  onQuery,
  onOpen,
}: Props & {
  query: LeaveRequestQuery;
  onQuery: (query: LeaveRequestQuery) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LeaveRequestsController(leave.loadRequests, access, query),
    [leave.loadRequests, access, query],
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
  const filters = useLeaveFilters(query, onQuery);
  const rows = useMemo(
    () => (state.page ? leaveRequestsView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <LeaveRequestsPage
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      filters={filters}
      firstPage={query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onQuery({ ...query, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onQuery({ ...query, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
