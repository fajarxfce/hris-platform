import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canReadWorkPeriods } from "../../domain/policies/work-period-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useWorkPeriodSnapshot } from "../controllers/use-work-period-snapshot";
import {
  attendanceAdjustmentHistoryPath,
  attendanceAdjustmentReviewPath,
} from "../models/attendance-adjustment-route";
import {
  workPeriodEmployeePath,
  workPeriodPath,
  workPeriodsPath,
} from "../models/work-period-route";
import { workPeriodSnapshotView } from "../models/work-period-view";
import { WorkPeriodSnapshotPage } from "../pages/work-period-snapshot-page";

export function WorkPeriodSnapshotScreen(props: WorkforceScreenProps) {
  const { month = "", employeeId = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${workPeriodsPath}?${new URLSearchParams({ company: props.access.companyId })}`}
        replace
      />
    );
  if (!parameters.has("company"))
    return (
      <Navigate to={workPeriodEmployeePath(props.access.companyId, month, employeeId)} replace />
    );
  return (
    <WorkPeriodSnapshotBinding
      key={`${props.accountId}:${props.access.companyId}:${month}:${employeeId}`}
      {...props}
      month={month}
      employee={employeeId}
    />
  );
}

function WorkPeriodSnapshotBinding(
  props: WorkforceScreenProps & { month: string; employee: string },
) {
  const navigate = useNavigate();
  const state = useWorkPeriodSnapshot(
    props.accountId,
    props.access,
    props.month,
    props.employee,
    props.workforce.loadWorkPeriodSnapshot,
  );
  const view = useMemo(
    () => (state.snapshot ? workPeriodSnapshotView(state.snapshot, props.locale) : null),
    [state.snapshot, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <WorkPeriodSnapshotPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      adjustmentsTo={
        state.snapshot?.adjustmentsSupported
          ? attendanceAdjustmentHistoryPath(props.access.companyId, props.month, props.employee)
          : null
      }
      onReviewDay={
        state.snapshot?.adjustmentsSupported
          ? (date) =>
              navigate(
                attendanceAdjustmentReviewPath(
                  props.access.companyId,
                  props.month,
                  props.employee,
                  date,
                ),
              )
          : null
      }
      backTo={
        canReadWorkPeriods(props.access.permissions)
          ? workPeriodPath(props.access.companyId, props.month)
          : null
      }
    />
  );
}
