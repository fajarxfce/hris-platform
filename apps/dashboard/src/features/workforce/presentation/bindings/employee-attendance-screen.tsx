import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useAttendanceFilters } from "../controllers/use-attendance-filters";
import { useEmployeeAttendance } from "../controllers/use-employee-attendance";
import {
  attendanceDayPath,
  attendancePath,
  employeeAttendancePath,
} from "../models/attendance-route";
import { attendanceOverviewView } from "../models/attendance-view";
import { calendarParameters, calendarRange } from "../models/calendar-route";
import { EmployeeAttendancePage } from "../pages/employee-attendance-page";

export function EmployeeAttendanceScreen(props: WorkforceScreenProps) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const range = useMemo(
    () => calendarRange(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={attendancePath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={
          employeeAttendancePath(employeeId) +
          "?" +
          calendarParameters(props.access.companyId, range)
        }
        replace
      />
    );
  return (
    <EmployeeAttendanceBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
      range={range}
      onRange={(next) => setParameters(calendarParameters(props.access.companyId, next))}
      onDay={(day) =>
        navigate(
          attendanceDayPath(employeeId, day) +
            "?" +
            calendarParameters(props.access.companyId, range),
        )
      }
    />
  );
}
function EmployeeAttendanceBinding(
  props: WorkforceScreenProps & {
    employeeId: string;
    range: CalendarRange;
    onRange: (range: CalendarRange) => void;
    onDay: (day: string) => void;
  },
) {
  const state = useEmployeeAttendance(
    props.accountId,
    props.access,
    props.employeeId,
    props.range,
    props.workforce.loadEmployeeAttendance,
  );
  const filters = useAttendanceFilters(props.range, props.locale, props.onRange);
  const rows = useMemo(
    () => (state.attendance ? attendanceOverviewView(state.attendance, props.locale) : []),
    [state.attendance, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <EmployeeAttendancePage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${attendancePath}?company=${encodeURIComponent(props.access.companyId)}`}
      onDay={props.onDay}
    />
  );
}
