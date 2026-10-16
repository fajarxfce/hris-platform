import { useContext, useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useAttendanceAdjustments } from "../controllers/use-attendance-adjustments";
import {
  attendanceAdjustmentHistoryPath,
  attendanceAdjustmentPath,
} from "../models/attendance-adjustment-route";
import { workPeriodEmployeePath, workPeriodsPath } from "../models/work-period-route";
import { AttendanceAdjustmentHistoryPage } from "../pages/attendance-adjustment-history-page";

export function AttendanceAdjustmentHistoryScreen(props: WorkforceScreenProps) {
  const { employeeId = "", month = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={workPeriodsPath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={attendanceAdjustmentHistoryPath(
          props.access.companyId,
          month,
          employeeId,
          parameters.get("before"),
        )}
        replace
      />
    );
  return (
    <AttendanceAdjustmentHistoryBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${month}`}
      {...props}
      employee={employeeId}
      month={month}
      before={parameters.get("before")}
    />
  );
}
function AttendanceAdjustmentHistoryBinding(
  props: WorkforceScreenProps & { employee: string; month: string; before: string | null },
) {
  const visible = useContext(WorkspaceVisibilityContext),
    navigate = useNavigate();
  const state = useAttendanceAdjustments(
    props.accountId,
    props.access,
    props.employee,
    props.month,
    props.before,
    visible,
    props.workforce.loadAttendanceAdjustments,
  );
  useWorkspaceRevalidation(state.failure);
  const rows = useMemo(() => {
    const date = new Intl.DateTimeFormat(props.locale, {
      timeZone: state.history?.period.timezone ?? props.timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
    return (state.history?.items ?? []).map((item) => ({
      id: item.id,
      actionLabel: `${item.revision} · ${item.workDate}`,
      cells: [
        String(item.revision),
        item.workDate,
        date.format(new Date(item.recordedAt)),
        item.reason,
      ],
    }));
  }, [state.history, props.locale, props.timezone]);
  return (
    <AttendanceAdjustmentHistoryPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      month={props.month}
      backTo={workPeriodEmployeePath(props.access.companyId, props.month, props.employee)}
      firstPage={props.before === null}
      onFirst={() =>
        navigate(
          attendanceAdjustmentHistoryPath(props.access.companyId, props.month, props.employee),
        )
      }
      onNext={() => {
        if (state.history?.nextCursor)
          navigate(
            attendanceAdjustmentHistoryPath(
              props.access.companyId,
              props.month,
              props.employee,
              state.history.nextCursor,
            ),
          );
      }}
      onOpen={(id) =>
        navigate(
          attendanceAdjustmentPath(
            props.access.companyId,
            props.month,
            props.employee,
            id,
            props.before,
          ),
        )
      }
    />
  );
}
