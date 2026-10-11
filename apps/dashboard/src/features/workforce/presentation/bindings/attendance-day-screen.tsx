import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canCorrectAttendance, canReviewAttendance } from "../../domain/policies/attendance-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useEmployeeAttendance } from "../controllers/use-employee-attendance";
import {
  attendanceDayPath,
  attendancePath,
  employeeAttendancePath,
} from "../models/attendance-route";
import { attendanceDayView } from "../models/attendance-view";
import { calendarParameters, calendarRange } from "../models/calendar-route";
import { AttendanceDayPage } from "../pages/attendance-day-page";

export function AttendanceDayScreen(props: WorkforceScreenProps) {
  const { employeeId = "", workDate = "" } = useParams();
  const [parameters] = useSearchParams();
  const range = calendarRange(parameters, props.access.companyId, workDate);
  const pinned = calendarParameters(props.access.companyId, range).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={attendancePath} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${attendanceDayPath(employeeId, workDate)}?${pinned}`} replace />;
  return (
    <AttendanceDayBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${workDate}`}
      {...props}
      employeeId={employeeId}
      date={workDate}
      pinned={pinned}
    />
  );
}
function AttendanceDayBinding(
  props: WorkforceScreenProps & { employeeId: string; date: string; pinned: string },
) {
  const navigate = useNavigate();
  const range = useMemo(() => ({ from: props.date, until: props.date }), [props.date]);
  const state = useEmployeeAttendance(
    props.accountId,
    props.access,
    props.employeeId,
    range,
    props.workforce.loadEmployeeAttendance,
  );
  const day = state.attendance?.days[0];
  const view = useMemo(
    () => (day ? attendanceDayView(day, props.locale, props.timezone) : null),
    [day, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  const base = attendanceDayPath(props.employeeId, props.date);
  return (
    <AttendanceDayPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      date={props.date}
      backTo={`${employeeAttendancePath(props.employeeId)}?${props.pinned}`}
      correctionTo={
        canCorrectAttendance(props.access.permissions) && day?.pendingCount === 0
          ? `${base}/correction?${props.pinned}`
          : null
      }
      onReview={
        canReviewAttendance(props.access.permissions)
          ? (id) => navigate(`${base}/review/${encodeURIComponent(id)}?${props.pinned}`)
          : null
      }
    />
  );
}
