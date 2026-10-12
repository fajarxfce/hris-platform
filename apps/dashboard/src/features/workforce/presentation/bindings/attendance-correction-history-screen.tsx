import { useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useAttendanceCorrectionHistory } from "../controllers/use-attendance-correction-history";
import { attendanceCorrectionHistoryView } from "../models/attendance-correction-history-view";
import {
  attendanceCorrectionHistoryPath,
  attendanceDayPath,
  attendanceHistoryParameters,
  attendancePath,
} from "../models/attendance-route";
import { calendarRange } from "../models/calendar-route";
import { AttendanceCorrectionHistoryPage } from "../pages/attendance-correction-history-page";

export function AttendanceCorrectionHistoryScreen(props: WorkforceScreenProps) {
  const { employeeId = "", workDate = "" } = useParams();
  const [parameters] = useSearchParams();
  const range = calendarRange(parameters, props.access.companyId, workDate);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={attendancePath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${attendanceCorrectionHistoryPath(employeeId, workDate)}?${attendanceHistoryParameters(
          props.access.companyId,
          range,
          parameters.get("after"),
          parameters.get("change"),
        )}`}
        replace
      />
    );
  return (
    <AttendanceCorrectionHistoryBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${workDate}`}
      {...props}
      employeeId={employeeId}
      workDate={workDate}
      range={range}
    />
  );
}
function AttendanceCorrectionHistoryBinding(
  props: WorkforceScreenProps & { employeeId: string; workDate: string; range: CalendarRange },
) {
  const [parameters, setParameters] = useSearchParams();
  const after = parameters.get("after"),
    selected = parameters.get("change");
  const state = useAttendanceCorrectionHistory(
    props.accountId,
    props.access,
    props.employeeId,
    props.workDate,
    after,
    props.workforce.loadAttendanceCorrectionHistory,
  );
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () =>
      state.history
        ? attendanceCorrectionHistoryView(state.history, selected, props.locale, props.timezone)
        : null,
    [state.history, selected, props.locale, props.timezone],
  );
  return (
    <AttendanceCorrectionHistoryPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${attendanceDayPath(props.employeeId, props.workDate)}?${attendanceHistoryParameters(props.access.companyId, props.range)}`}
      firstPage={after === null}
      onFirst={() =>
        setParameters(attendanceHistoryParameters(props.access.companyId, props.range))
      }
      onNext={() => {
        if (state.history?.nextCursor !== null && state.history?.nextCursor !== undefined)
          setParameters(
            attendanceHistoryParameters(
              props.access.companyId,
              props.range,
              state.history.nextCursor,
            ),
          );
      }}
      onOpenChange={(version) =>
        setParameters(
          attendanceHistoryParameters(props.access.companyId, props.range, after, version),
        )
      }
      onCloseChange={() =>
        setParameters(attendanceHistoryParameters(props.access.companyId, props.range, after), {
          replace: true,
        })
      }
    />
  );
}
