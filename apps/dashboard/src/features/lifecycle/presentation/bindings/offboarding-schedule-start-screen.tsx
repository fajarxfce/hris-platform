import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canScheduleOffboarding } from "../../domain/policies/offboarding-schedule-policy";
import type { OffboardingScheduleScreenProps } from "../contracts/offboarding-schedule-screen-props";
import { OffboardingScheduleStartController } from "../controllers/offboarding-schedule-start-controller";
import { useLifecycleScheduleReasonForm } from "../controllers/use-lifecycle-schedule-reason-form";
import { offboardingScheduleMessages } from "../i18n/offboarding-schedule-messages";
import { offboardingScheduleRoute } from "../models/offboarding-schedule-route";
import { offboardingScheduleReviewView } from "../models/offboarding-schedule-view";
import { OffboardingScheduleCommandPage } from "../pages/offboarding-schedule-command-page";
export function OffboardingScheduleStartScreen(props: OffboardingScheduleScreenProps) {
  const { caseId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = offboardingScheduleRoute(props.access.companyId, caseId, parameters, "/new");
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OffboardingScheduleStartBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}`}
      {...props}
      caseId={caseId}
    />
  );
}
function OffboardingScheduleStartBinding(
  props: OffboardingScheduleScreenProps & { caseId: string },
) {
  const [parameters] = useSearchParams(),
    route = offboardingScheduleRoute(props.access.companyId, props.caseId, parameters),
    text = offboardingScheduleMessages(props.locale);
  const controller = useMemo(
    () =>
      new OffboardingScheduleStartController(
        props.lifecycle,
        props.access,
        props.caseId,
        props.nextIdentifier,
      ),
    [props.lifecycle, props.access, props.caseId, props.nextIdentifier],
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
  const form = useLifecycleScheduleReasonForm(controller, state),
    view = useMemo(
      () => (state.review ? offboardingScheduleReviewView(state.review, props.locale) : null),
      [state.review, props.locale],
    );
  return (
    <OffboardingScheduleCommandPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      title={text.new}
      note={text.createNote}
      canSubmit={state.review !== null && canScheduleOffboarding(props.access, state.review)}
      properties={view?.properties ?? []}
      issues={view?.issues ?? []}
      backTo={route.listTo}
      backLabel={text.backToSchedules}
      successTo={
        state.scheduleId
          ? `${route.path}/${encodeURIComponent(state.scheduleId)}?${route.query}`
          : null
      }
      successMessage={text.scheduled}
      onRetry={controller.retry}
    />
  );
}
