import { useContext, useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useAttendanceAdjustment } from "../controllers/use-attendance-adjustment";
import {
  attendanceAdjustmentHistoryPath,
  attendanceAdjustmentPath,
} from "../models/attendance-adjustment-route";
import { attendanceAdjustmentSummaryView } from "../models/attendance-adjustment-view";
import {
  type AttendanceClosingOrigin,
  attendanceClosingOrigin,
  withAttendanceClosingOrigin,
} from "../models/attendance-closing-origin";
import { workPeriodEmployeePath, workPeriodsPath } from "../models/work-period-route";
import { workPeriodSnapshotView } from "../models/work-period-view";
import { AttendanceAdjustmentDetailsPage } from "../pages/attendance-adjustment-details-page";

export function AttendanceAdjustmentDetailsScreen(props: WorkforceScreenProps) {
  const { employeeId = "", month = "", adjustmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={workPeriodsPath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={attendanceAdjustmentPath(
          props.access.companyId,
          month,
          employeeId,
          adjustmentId,
          parameters.get("before"),
        )}
        replace
      />
    );
  return (
    <AttendanceAdjustmentDetailsBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${month}:${adjustmentId}`}
      {...props}
      origin={attendanceClosingOrigin(parameters, props.access.companyId, month)}
      employee={employeeId}
      month={month}
      id={adjustmentId}
      before={parameters.get("before")}
    />
  );
}
function AttendanceAdjustmentDetailsBinding(
  props: WorkforceScreenProps & {
    origin: AttendanceClosingOrigin | null;
    employee: string;
    month: string;
    id: string;
    before: string | null;
  },
) {
  const visible = useContext(WorkspaceVisibilityContext);
  const state = useAttendanceAdjustment(
    props.accountId,
    props.access,
    props.employee,
    props.month,
    props.id,
    visible,
    props.workforce.loadAttendanceAdjustment,
  );
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.details ? workPeriodSnapshotView(state.details.snapshot, props.locale) : null),
    [state.details, props.locale],
  );
  const properties = useMemo(
    () =>
      state.details
        ? attendanceAdjustmentSummaryView(
            state.details.summary,
            props.locale,
            state.details.snapshot.period.timezone ?? props.timezone,
          )
        : [],
    [state.details, props.locale, props.timezone],
  );
  return (
    <AttendanceAdjustmentDetailsPage
      state={state}
      view={view}
      properties={properties}
      locale={props.locale}
      reportTo={props.origin?.to ?? null}
      companyName={props.companyName}
      backTo={attendanceAdjustmentHistoryPath(
        props.access.companyId,
        props.month,
        props.employee,
        props.before,
      )}
      originalTo={withAttendanceClosingOrigin(
        workPeriodEmployeePath(props.access.companyId, props.month, props.employee),
        props.origin,
      )}
    />
  );
}
