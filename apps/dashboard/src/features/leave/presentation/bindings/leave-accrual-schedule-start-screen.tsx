import { useContext, useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveAccrualScheduleScreenProps } from "../contracts/leave-accrual-schedule-screen-props";
import { LeaveAccrualScheduleStartController } from "../controllers/leave-accrual-schedule-start-controller";
import { useLeaveAccrualScheduleForm } from "../controllers/use-leave-accrual-schedule-form";
import { leaveAccrualScheduleRoute } from "../models/leave-accrual-schedule-route";
import { leaveAccrualScheduleReviewView } from "../models/leave-accrual-schedule-view";
import { LeaveAccrualScheduleStartPage } from "../pages/leave-accrual-schedule-start-page";
import { LeaveBatchTypePicker } from "./leave-batch-type-picker";
export function LeaveAccrualScheduleStartScreen(props: LeaveAccrualScheduleScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(props.access.companyId, parameters, "/new");
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveAccrualScheduleStartBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
    />
  );
}
function LeaveAccrualScheduleStartBinding(props: LeaveAccrualScheduleScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveAccrualScheduleRoute(props.access.companyId, parameters),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new LeaveAccrualScheduleStartController(props.leave, props.access, props.nextIdentifier),
    [props.leave, props.access, props.nextIdentifier],
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
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const form = useLeaveAccrualScheduleForm(controller, state, props.access.companyId, today),
    formId = useId();
  const view = useMemo(
    () => (state.review ? leaveAccrualScheduleReviewView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  return (
    <>
      <LeaveAccrualScheduleStartPage
        state={state}
        form={form}
        view={view}
        locale={props.locale}
        companyName={props.companyName}
        backTo={route.listTo}
        formId={formId}
        onRetry={controller.retry}
        successTo={
          state.scheduleId
            ? `${route.path}/${encodeURIComponent(state.scheduleId)}?${route.query}`
            : null
        }
      />
      {form.pickerOpen && state.stage === "editing" && (
        <LeaveBatchTypePicker
          {...props}
          kind="ACCRUAL"
          onSelect={form.selectType}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}
