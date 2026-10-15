import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canReadOffboardingReview } from "../../domain/policies/offboarding-policy";
import type { OffboardingScheduleScreenProps } from "../contracts/offboarding-schedule-screen-props";
import { OffboardingSchedulesController } from "../controllers/offboarding-schedules-controller";
import { offboardingScheduleRoute } from "../models/offboarding-schedule-route";
import { offboardingSchedulesView } from "../models/offboarding-schedule-view";
import { OffboardingSchedulesPage } from "../pages/offboarding-schedules-page";
export function OffboardingSchedulesScreen(props: OffboardingScheduleScreenProps) {
  const { caseId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = offboardingScheduleRoute(props.access.companyId, caseId, parameters);
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OffboardingSchedulesBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}`}
      {...props}
      caseId={caseId}
    />
  );
}
function OffboardingSchedulesBinding(props: OffboardingScheduleScreenProps & { caseId: string }) {
  const navigate = useNavigate(),
    [parameters] = useSearchParams(),
    route = offboardingScheduleRoute(props.access.companyId, props.caseId, parameters);
  const controller = useMemo(
    () => new OffboardingSchedulesController(props.lifecycle, props.access, props.caseId),
    [props.lifecycle, props.access, props.caseId],
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
    () => offboardingSchedulesView(state.items ?? [], props.locale),
    [state.items, props.locale],
  );
  const canCreate =
    state.stage === "ready" &&
    state.case?.kind === "OFFBOARDING" &&
    state.case.status === "OPEN" &&
    state.items !== null &&
    state.items.length < 8 &&
    !state.items.some((item) => item.status === "SCHEDULED") &&
    canReadOffboardingReview(props.access.permissions);
  return (
    <OffboardingSchedulesPage
      state={state}
      rows={rows}
      companyName={props.companyName}
      locale={props.locale}
      backTo={route.caseTo}
      newTo={canCreate ? route.newTo : null}
      onRefresh={controller.refresh}
      onOpen={(id) => navigate(`${route.path}/${encodeURIComponent(id)}?${route.query}`)}
    />
  );
}
