import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveCarryExpiryScreenProps } from "../contracts/leave-carry-expiry-screen-props";
import { LeaveCarryExpiriesController } from "../controllers/leave-carry-expiries-controller";
import { leaveCarryExpiryRoute } from "../models/leave-carry-expiry-route";
import { leaveCarryExpiriesView } from "../models/leave-carry-expiry-view";
import { LeaveCarryExpiriesPage } from "../pages/leave-carry-expiries-page";
import { useLeaveCarryVisibility } from "./use-leave-carry-visibility";
export function LeaveCarryExpiriesScreen(props: LeaveCarryExpiryScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveCarryExpiryRoute(props.access.companyId, parameters);
  if (route.foreignCompany || !route.canonical) return <Navigate to={route.listTo} replace />;
  return (
    <LeaveCarryExpiriesBinding
      key={`${props.accountId}:${props.access.companyId}:${route.after}`}
      {...props}
      after={route.after}
    />
  );
}
function LeaveCarryExpiriesBinding(props: LeaveCarryExpiryScreenProps & { after: string | null }) {
  const [parameters, setParameters] = useSearchParams(),
    route = leaveCarryExpiryRoute(props.access.companyId, parameters);
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new LeaveCarryExpiriesController(props.leave.loadCarryExpiries, props.access, props.after),
    [props.leave.loadCarryExpiries, props.access, props.after],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useLeaveCarryVisibility(controller);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const rows = useMemo(
    () => leaveCarryExpiriesView(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <LeaveCarryExpiriesPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      processingTo={route.processingTo}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters({ company: props.access.companyId })}
      onNext={() => {
        if (state.stage === "ready" && state.page?.nextCursor)
          setParameters({ company: props.access.companyId, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.stage === "ready" && state.page?.items.some((item) => item.id === id))
          void navigate(`${route.path}/${encodeURIComponent(id)}?${route.query}`);
      }}
    />
  );
}
