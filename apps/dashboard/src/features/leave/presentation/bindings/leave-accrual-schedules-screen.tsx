import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canScheduleLeaveAccrual } from "../../domain/policies/leave-accrual-schedule-policy";
import type { LeaveAccrualScheduleScreenProps } from "../contracts/leave-accrual-schedule-screen-props";
import { LeaveAccrualSchedulesController } from "../controllers/leave-accrual-schedules-controller";
import { leaveAccrualScheduleRoute } from "../models/leave-accrual-schedule-route";
import { leaveAccrualSchedulesView } from "../models/leave-accrual-schedule-view";
import { LeaveAccrualSchedulesPage } from "../pages/leave-accrual-schedules-page";
export function LeaveAccrualSchedulesScreen(props: LeaveAccrualScheduleScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(props.access.companyId, parameters);
  if (route.foreignCompany || !route.canonical) return <Navigate to={route.listTo} replace />;
  return (
    <LeaveAccrualSchedulesBinding
      key={`${props.accountId}:${props.access.companyId}:${route.after}`}
      {...props}
      after={route.after}
    />
  );
}
function LeaveAccrualSchedulesBinding(
  props: LeaveAccrualScheduleScreenProps & { after: string | null },
) {
  const [parameters, setParameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(props.access.companyId, parameters),
    navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () =>
      new LeaveAccrualSchedulesController(
        props.leave.loadAccrualSchedules,
        props.access,
        props.after,
      ),
    [props.leave.loadAccrualSchedules, props.access, props.after],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    if (visible) controller.activate();
    return controller.deactivate;
  }, [controller, visible]);
  useWorkspaceRevalidation(state.failure);
  const rows = useMemo(
    () => leaveAccrualSchedulesView(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <LeaveAccrualSchedulesPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      createTo={canScheduleLeaveAccrual(props.access.permissions) ? route.createTo : null}
      batchesTo={route.batchesTo}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters({ company: props.access.companyId })}
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({ company: props.access.companyId, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((item) => item.id === id))
          void navigate(`${route.path}/${encodeURIComponent(id)}?${route.query}`);
      }}
    />
  );
}
