import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { OffboardingScheduleAction } from "../../domain/entities/offboarding-schedule";
import { canChangeOffboardingSchedule } from "../../domain/policies/offboarding-schedule-policy";
import type { OffboardingScheduleScreenProps } from "../contracts/offboarding-schedule-screen-props";
import { OffboardingScheduleChangeController } from "../controllers/offboarding-schedule-change-controller";
import { useLifecycleScheduleReasonForm } from "../controllers/use-lifecycle-schedule-reason-form";
import { offboardingScheduleMessages } from "../i18n/offboarding-schedule-messages";
import { offboardingScheduleRoute } from "../models/offboarding-schedule-route";
import { offboardingScheduleView } from "../models/offboarding-schedule-view";
import { OffboardingScheduleCommandPage } from "../pages/offboarding-schedule-command-page";

type Props = OffboardingScheduleScreenProps & { action: OffboardingScheduleAction };
export function OffboardingScheduleChangeScreen(props: Props) {
  const { caseId = "", scheduleId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = offboardingScheduleRoute(
    props.access.companyId,
    caseId,
    parameters,
    `/${encodeURIComponent(scheduleId)}/${props.action}`,
  );
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OffboardingScheduleChangeBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}:${scheduleId}:${props.action}`}
      {...props}
      caseId={caseId}
      id={scheduleId}
    />
  );
}
function OffboardingScheduleChangeBinding(props: Props & { caseId: string; id: string }) {
  const [parameters] = useSearchParams(),
    route = offboardingScheduleRoute(
      props.access.companyId,
      props.caseId,
      parameters,
      `/${encodeURIComponent(props.id)}`,
    ),
    text = offboardingScheduleMessages(props.locale);
  const command =
    props.action === "cancel"
      ? props.lifecycle.cancelOffboardingSchedule
      : props.lifecycle.recoverOffboardingSchedule;
  const controller = useMemo(
    () =>
      new OffboardingScheduleChangeController(
        props.lifecycle.loadOffboardingSchedule,
        command,
        props.action,
        props.access,
        props.caseId,
        props.id,
        props.nextIdentifier,
      ),
    [
      props.lifecycle.loadOffboardingSchedule,
      command,
      props.action,
      props.access,
      props.caseId,
      props.id,
      props.nextIdentifier,
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
  const form = useLifecycleScheduleReasonForm(controller, state),
    view = useMemo(
      () => (state.review ? offboardingScheduleView(state.review, props.locale) : null),
      [state.review, props.locale],
    );
  return (
    <OffboardingScheduleCommandPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      title={text[props.action]}
      note={props.action === "cancel" ? text.cancelNote : text.recoverNote}
      canSubmit={
        state.review !== null &&
        canChangeOffboardingSchedule(props.access, state.review, props.action)
      }
      properties={view ? [...view.properties, ...view.job] : []}
      issues={[]}
      backTo={route.current}
      backLabel={text.backToSchedule}
      successTo={
        state.scheduleId
          ? `${route.path}/${encodeURIComponent(state.scheduleId)}?${route.query}`
          : null
      }
      successMessage={props.action === "cancel" ? text.cancelled : text.recovered}
      onRetry={controller.retry}
    />
  );
}
