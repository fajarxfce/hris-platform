import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canChangeOnboardingSchedule } from "../../domain/policies/onboarding-schedule-policy";
import type { OnboardingScheduleScreenProps } from "../contracts/onboarding-schedule-screen-props";
import { OnboardingScheduleController } from "../controllers/onboarding-schedule-controller";
import { onboardingScheduleRoute } from "../models/onboarding-schedule-route";
import { onboardingScheduleView } from "../models/onboarding-schedule-view";
import { OnboardingSchedulePage } from "../pages/onboarding-schedule-page";
export function OnboardingScheduleScreen(props: OnboardingScheduleScreenProps) {
  const { employeeId = "", scheduleId = "" } = useParams(),
    [parameters] = useSearchParams(),
    today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const route = onboardingScheduleRoute(
    props.access.companyId,
    employeeId,
    parameters,
    today,
    `/${encodeURIComponent(scheduleId)}`,
  );
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OnboardingScheduleBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${scheduleId}`}
      {...props}
      employeeId={employeeId}
      id={scheduleId}
      asOf={route.asOf}
    />
  );
}
function OnboardingScheduleBinding(
  props: OnboardingScheduleScreenProps & { employeeId: string; id: string; asOf: string },
) {
  const [parameters] = useSearchParams(),
    route = onboardingScheduleRoute(
      props.access.companyId,
      props.employeeId,
      parameters,
      props.asOf,
    ),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () =>
      new OnboardingScheduleController(
        props.lifecycle.loadOnboardingSchedule,
        props.access,
        props.employeeId,
        props.id,
      ),
    [props.lifecycle.loadOnboardingSchedule, props.access, props.employeeId, props.id],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const changed = () => controller.setVisible(visible && document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, [controller, visible]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.details ? onboardingScheduleView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  const acquired = state.stage === "ready" && !state.failure ? state.details : null;
  return (
    <OnboardingSchedulePage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={route.listTo}
      employeeTo={route.employeeTo}
      caseTo={
        acquired?.status === "STARTED"
          ? `/people/lifecycle/cases/${encodeURIComponent(acquired.caseId)}?${new URLSearchParams({ company: props.access.companyId, status: "OPEN" })}`
          : null
      }
      cancelTo={
        acquired && canChangeOnboardingSchedule(props.access, acquired, "cancel")
          ? `${route.path}/${encodeURIComponent(props.id)}/cancel?${route.query}`
          : null
      }
      recoverTo={
        acquired && canChangeOnboardingSchedule(props.access, acquired, "recover")
          ? `${route.path}/${encodeURIComponent(props.id)}/recover?${route.query}`
          : null
      }
      jobTo={
        acquired &&
        (acquired.createdBy === props.accountId || props.access.permissions.includes("jobs.read"))
          ? `/administration/jobs?${new URLSearchParams({ company: props.access.companyId, job: acquired.job.id })}`
          : null
      }
      onRefresh={controller.refresh}
    />
  );
}
