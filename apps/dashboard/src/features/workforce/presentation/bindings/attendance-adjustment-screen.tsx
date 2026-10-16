import { useContext, useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { AttendanceAdjustmentController } from "../controllers/attendance-adjustment-controller";
import { useAttendanceAdjustmentForm } from "../controllers/use-attendance-adjustment-form";
import {
  attendanceAdjustmentHistoryPath,
  attendanceAdjustmentPath,
  attendanceAdjustmentReviewPath,
} from "../models/attendance-adjustment-route";
import { attendanceAdjustmentReviewView } from "../models/attendance-adjustment-view";
import { workPeriodEmployeePath, workPeriodsPath } from "../models/work-period-route";
import { AttendanceAdjustmentPage } from "../pages/attendance-adjustment-page";

export function AttendanceAdjustmentScreen(props: WorkforceScreenProps) {
  const { employeeId = "", month = "", workDate = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={workPeriodsPath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={attendanceAdjustmentReviewPath(props.access.companyId, month, employeeId, workDate)}
        replace
      />
    );
  return (
    <AttendanceAdjustmentBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${month}:${workDate}`}
      {...props}
      employee={employeeId}
      month={month}
      date={workDate}
    />
  );
}
function AttendanceAdjustmentBinding(
  props: WorkforceScreenProps & { employee: string; month: string; date: string },
) {
  const visible = useContext(WorkspaceVisibilityContext),
    formId = useId();
  const controller = useMemo(
    () =>
      new AttendanceAdjustmentController(
        props.workforce,
        props.access,
        props.employee,
        props.date.slice(0, 7) === props.month ? props.date : "",
        props.nextIdentifier,
      ),
    [props.workforce, props.access, props.employee, props.month, props.date, props.nextIdentifier],
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
  useEffect(() => {
    const changed = () => controller.setVisible(visible && document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, [controller, visible]);
  const timezone = state.review?.period.timezone ?? props.timezone;
  const form = useAttendanceAdjustmentForm(controller, state, timezone);
  const view = useMemo(
    () => (state.review ? attendanceAdjustmentReviewView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <AttendanceAdjustmentPage
      state={state}
      form={form}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      timezone={timezone}
      formId={formId}
      backTo={workPeriodEmployeePath(props.access.companyId, props.month, props.employee)}
      historyTo={attendanceAdjustmentHistoryPath(
        props.access.companyId,
        props.month,
        props.employee,
      )}
      receiptTo={
        state.receipt
          ? attendanceAdjustmentPath(
              props.access.companyId,
              props.month,
              props.employee,
              state.receipt.id,
            )
          : null
      }
      onRetry={controller.retry}
    />
  );
}
