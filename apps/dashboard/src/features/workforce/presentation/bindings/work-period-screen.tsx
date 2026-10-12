import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useWorkPeriodMonth } from "../controllers/use-work-period-month";
import { WorkPeriodController } from "../controllers/work-period-controller";
import {
  defaultWorkPeriodMonth,
  workPeriodActionPath,
  workPeriodPath,
} from "../models/work-period-route";
import { workPeriodOverviewView } from "../models/work-period-view";
import { WorkPeriodPage } from "../pages/work-period-page";
import { WorkPeriodEmployees } from "./work-period-employees";

export function WorkPeriodScreen(props: WorkforceScreenProps) {
  const { month = "" } = useParams();
  const [parameters] = useSearchParams();
  const defaultMonth = useMemo(
    () => defaultWorkPeriodMonth(companyDate(props.timezone, new Date())),
    [props.timezone],
  );
  if (!month || (parameters.has("company") && parameters.get("company") !== props.access.companyId))
    return <Navigate to={workPeriodPath(props.access.companyId, defaultMonth)} replace />;
  if (!parameters.has("company"))
    return <Navigate to={workPeriodPath(props.access.companyId, month)} replace />;
  return (
    <WorkPeriodBinding
      key={`${props.accountId}:${props.access.companyId}:${month}`}
      {...props}
      month={month}
    />
  );
}

function WorkPeriodBinding(props: WorkforceScreenProps & { month: string }) {
  const { workforce, access, month, locale } = props;
  const navigate = useNavigate();
  const controller = useMemo(
    () => new WorkPeriodController(workforce.loadWorkPeriodOverview, access, month),
    [workforce, access, month],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const visibilityChanged = () => controller.setVisible(document.visibilityState !== "hidden");
    visibilityChanged();
    controller.activate();
    document.addEventListener("visibilitychange", visibilityChanged);
    return () => {
      document.removeEventListener("visibilitychange", visibilityChanged);
      controller.deactivate();
    };
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const filters = useWorkPeriodMonth(month, locale, (selected) => {
    if (selected === month) void controller.refresh();
    else navigate(workPeriodPath(access.companyId, selected));
  });
  const view = useMemo(
    () => (state.overview ? workPeriodOverviewView(state.overview, locale) : null),
    [state.overview, locale],
  );
  return (
    <WorkPeriodPage
      state={state}
      view={view}
      filters={filters}
      locale={locale}
      companyName={props.companyName}
      onRefresh={controller.refresh}
      onClose={() => navigate(workPeriodActionPath(access.companyId, month, "close"))}
      onRecover={() => navigate(workPeriodActionPath(access.companyId, month, "recover"))}
      jobTo={
        state.overview?.availableActions.includes("view_job") && state.overview.attempt
          ? `/jobs?${new URLSearchParams({ company: access.companyId, job: state.overview.attempt.id })}`
          : null
      }
    >
      {state.overview?.availableActions.includes("view_snapshots") &&
        state.overview.period?.jobId && (
          <WorkPeriodEmployees
            key={state.overview.period.jobId}
            {...props}
            jobId={state.overview.period.jobId}
          />
        )}
    </WorkPeriodPage>
  );
}
