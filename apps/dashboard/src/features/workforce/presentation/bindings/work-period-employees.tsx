import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useWorkPeriodEmployees } from "../controllers/use-work-period-employees";
import { workforceEmployeeView } from "../models/calendar-view";
import { workPeriodEmployeePath } from "../models/work-period-route";
import { WorkPeriodEmployeesPage } from "../pages/work-period-employees-page";

export function WorkPeriodEmployees(
  props: WorkforceScreenProps & { month: string; jobId: string },
) {
  const navigate = useNavigate();
  const state = useWorkPeriodEmployees(
    props.accountId,
    props.access,
    props.month,
    props.jobId,
    props.workforce.loadWorkPeriodEmployees,
  );
  const rows = useMemo(
    () => (state.page ? workforceEmployeeView(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <WorkPeriodEmployeesPage
      state={state}
      rows={rows}
      locale={props.locale}
      onOpen={(id) => navigate(workPeriodEmployeePath(props.access.companyId, props.month, id))}
    />
  );
}
