import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canChangeOffboardingSchedule } from "../../domain/policies/offboarding-schedule-policy";
import type { OffboardingScheduleScreenProps } from "../contracts/offboarding-schedule-screen-props";
import { OffboardingScheduleController } from "../controllers/offboarding-schedule-controller";
import { offboardingScheduleRoute } from "../models/offboarding-schedule-route";
import { offboardingScheduleView } from "../models/offboarding-schedule-view";
import { OffboardingSchedulePage } from "../pages/offboarding-schedule-page";
export function OffboardingScheduleScreen(props: OffboardingScheduleScreenProps) {
  const { caseId = "", scheduleId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = offboardingScheduleRoute(
    props.access.companyId,
    caseId,
    parameters,
    `/${encodeURIComponent(scheduleId)}`,
  );
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OffboardingScheduleBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}:${scheduleId}`}
      {...props}
      caseId={caseId}
      id={scheduleId}
    />
  );
}
function OffboardingScheduleBinding(
  props: OffboardingScheduleScreenProps & { caseId: string; id: string },
) {
  const [parameters] = useSearchParams(),
    route = offboardingScheduleRoute(props.access.companyId, props.caseId, parameters),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () =>
      new OffboardingScheduleController(
        props.lifecycle.loadOffboardingSchedule,
        props.access,
        props.caseId,
        props.id,
      ),
    [props.lifecycle.loadOffboardingSchedule, props.access, props.caseId, props.id],
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
    () => (state.details ? offboardingScheduleView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  const acquired = state.stage === "ready" && !state.failure ? state.details : null;
  return (
    <OffboardingSchedulePage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={route.listTo}
      caseTo={route.caseTo}
      cancelTo={
        acquired && canChangeOffboardingSchedule(props.access, acquired, "cancel")
          ? `${route.path}/${encodeURIComponent(props.id)}/cancel?${route.query}`
          : null
      }
      recoverTo={
        acquired && canChangeOffboardingSchedule(props.access, acquired, "recover")
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
