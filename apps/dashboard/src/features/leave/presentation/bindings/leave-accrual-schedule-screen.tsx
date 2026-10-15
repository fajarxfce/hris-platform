import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveAccrualScheduleScreenProps } from "../contracts/leave-accrual-schedule-screen-props";
import { LeaveAccrualScheduleController } from "../controllers/leave-accrual-schedule-controller";
import {
  leaveAccrualSchedulePeriodLink,
  leaveAccrualScheduleRoute,
} from "../models/leave-accrual-schedule-route";
import { leaveAccrualScheduleView } from "../models/leave-accrual-schedule-view";
import { LeaveAccrualSchedulePage } from "../pages/leave-accrual-schedule-page";
export function LeaveAccrualScheduleScreen(props: LeaveAccrualScheduleScreenProps) {
  const { scheduleId = "" } = useParams(),
    [parameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(
      props.access.companyId,
      parameters,
      `/${encodeURIComponent(scheduleId)}`,
    );
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveAccrualScheduleBinding
      key={`${props.accountId}:${props.access.companyId}:${scheduleId}`}
      {...props}
      scheduleId={scheduleId}
    />
  );
}
function LeaveAccrualScheduleBinding(
  props: LeaveAccrualScheduleScreenProps & { scheduleId: string },
) {
  const [parameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(props.access.companyId, parameters),
    navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () =>
      new LeaveAccrualScheduleController(
        props.leave.loadAccrualSchedule,
        props.access,
        props.scheduleId,
      ),
    [props.leave.loadAccrualSchedule, props.access, props.scheduleId],
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
  const view = useMemo(
    () => (state.details ? leaveAccrualScheduleView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  return (
    <LeaveAccrualSchedulePage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={route.listTo}
      onRefresh={controller.refresh}
      onOpenPeriod={(id) => {
        if (state.details?.periods.some((period) => period.batch.id === id))
          void navigate(
            leaveAccrualSchedulePeriodLink(
              props.access.companyId,
              props.scheduleId,
              id,
              route.after,
            ),
          );
      }}
    />
  );
}
