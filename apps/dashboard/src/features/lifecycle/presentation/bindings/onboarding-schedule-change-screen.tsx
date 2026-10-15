import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { OnboardingScheduleAction } from "../../domain/entities/onboarding-schedule";
import { canChangeOnboardingSchedule } from "../../domain/policies/onboarding-schedule-policy";
import type { OnboardingScheduleScreenProps } from "../contracts/onboarding-schedule-screen-props";
import { OnboardingScheduleChangeController } from "../controllers/onboarding-schedule-change-controller";
import { useLifecycleScheduleReasonForm } from "../controllers/use-lifecycle-schedule-reason-form";
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import { onboardingScheduleRoute } from "../models/onboarding-schedule-route";
import { onboardingScheduleView } from "../models/onboarding-schedule-view";
import { OnboardingScheduleCommandPage } from "../pages/onboarding-schedule-command-page";
export function OnboardingScheduleChangeScreen(
  props: OnboardingScheduleScreenProps & { action: OnboardingScheduleAction },
) {
  const { employeeId = "", scheduleId = "" } = useParams(),
    [parameters] = useSearchParams(),
    today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const route = onboardingScheduleRoute(
    props.access.companyId,
    employeeId,
    parameters,
    today,
    `/${encodeURIComponent(scheduleId)}/${props.action}`,
  );
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OnboardingScheduleChangeBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${scheduleId}:${props.action}`}
      {...props}
      employeeId={employeeId}
      id={scheduleId}
      asOf={route.asOf}
    />
  );
}
function OnboardingScheduleChangeBinding(
  props: OnboardingScheduleScreenProps & {
    action: OnboardingScheduleAction;
    employeeId: string;
    id: string;
    asOf: string;
  },
) {
  const [parameters] = useSearchParams(),
    route = onboardingScheduleRoute(
      props.access.companyId,
      props.employeeId,
      parameters,
      props.asOf,
    ),
    text = onboardingScheduleMessages(props.locale);
  const command =
    props.action === "cancel"
      ? props.lifecycle.cancelOnboardingSchedule
      : props.lifecycle.recoverOnboardingSchedule;
  const controller = useMemo(
    () =>
      new OnboardingScheduleChangeController(
        props.lifecycle.loadOnboardingSchedule,
        command,
        props.action,
        props.access,
        props.employeeId,
        props.id,
        props.nextIdentifier,
      ),
    [
      props.lifecycle.loadOnboardingSchedule,
      command,
      props.action,
      props.access,
      props.employeeId,
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
      () => (state.review ? onboardingScheduleView(state.review, props.locale) : null),
      [state.review, props.locale],
    );
  return (
    <OnboardingScheduleCommandPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      title={props.action === "cancel" ? text.cancel : text.recover}
      note={props.action === "cancel" ? text.cancelNote : text.recoverNote}
      canSubmit={
        state.review !== null &&
        canChangeOnboardingSchedule(props.access, state.review, props.action)
      }
      properties={view?.properties ?? []}
      issues={[]}
      backTo={`${route.path}/${encodeURIComponent(props.id)}?${route.query}`}
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
