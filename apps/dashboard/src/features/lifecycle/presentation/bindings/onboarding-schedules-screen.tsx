import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canReadOnboardingSchedules } from "../../domain/policies/onboarding-schedule-policy";
import type { OnboardingScheduleScreenProps } from "../contracts/onboarding-schedule-screen-props";
import { OnboardingSchedulesController } from "../controllers/onboarding-schedules-controller";
import { onboardingScheduleRoute } from "../models/onboarding-schedule-route";
import { onboardingSchedulesView } from "../models/onboarding-schedule-view";
import { OnboardingSchedulesPage } from "../pages/onboarding-schedules-page";
export function OnboardingSchedulesScreen(props: OnboardingScheduleScreenProps) {
  const { employeeId = "" } = useParams(),
    [parameters] = useSearchParams(),
    today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const route = onboardingScheduleRoute(props.access.companyId, employeeId, parameters, today);
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OnboardingSchedulesBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${route.asOf}`}
      {...props}
      employeeId={employeeId}
      asOf={route.asOf}
    />
  );
}
function OnboardingSchedulesBinding(
  props: OnboardingScheduleScreenProps & { employeeId: string; asOf: string },
) {
  const navigate = useNavigate(),
    [parameters] = useSearchParams(),
    route = onboardingScheduleRoute(
      props.access.companyId,
      props.employeeId,
      parameters,
      props.asOf,
    );
  const controller = useMemo(
    () =>
      new OnboardingSchedulesController(
        props.loadEmployee,
        props.lifecycle.loadOnboardingSchedules,
        props.access,
        props.employeeId,
        props.asOf,
      ),
    [
      props.loadEmployee,
      props.lifecycle.loadOnboardingSchedules,
      props.access,
      props.employeeId,
      props.asOf,
    ],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const rows = useMemo(
    () => onboardingSchedulesView(state.items ?? [], props.locale),
    [state.items, props.locale],
  );
  const canCreate =
    state.stage === "ready" &&
    state.employee !== null &&
    state.employee.terms.status !== "ENDED" &&
    state.items !== null &&
    state.items.length < 8 &&
    !state.items.some((item) => item.status === "SCHEDULED") &&
    canReadOnboardingSchedules(props.access.permissions);
  return (
    <OnboardingSchedulesPage
      state={state}
      rows={rows}
      context={
        state.employee ? `${props.companyName} / ${state.employee.legalName}` : props.companyName
      }
      locale={props.locale}
      backTo={route.employeeTo}
      newTo={canCreate ? route.newTo : null}
      onRefresh={controller.refresh}
      onOpen={(id) => navigate(`${route.path}/${encodeURIComponent(id)}?${route.query}`)}
    />
  );
}
