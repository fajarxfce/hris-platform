import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canScheduleOnboarding } from "../../domain/policies/onboarding-schedule-policy";
import type { OnboardingScheduleScreenProps } from "../contracts/onboarding-schedule-screen-props";
import { OnboardingScheduleStartController } from "../controllers/onboarding-schedule-start-controller";
import { useOnboardingScheduleForm } from "../controllers/use-onboarding-schedule-form";
import { onboardingScheduleRoute } from "../models/onboarding-schedule-route";
import {
  onboardingScheduleReviewView,
  onboardingTaskRows,
} from "../models/onboarding-schedule-view";
import { OnboardingScheduleStartPage } from "../pages/onboarding-schedule-start-page";
import { LifecycleAssigneePicker } from "./lifecycle-assignee-picker";
import { LifecycleTemplatePicker } from "./lifecycle-template-picker";
export function OnboardingScheduleStartScreen(props: OnboardingScheduleScreenProps) {
  const { employeeId = "" } = useParams(),
    [parameters] = useSearchParams(),
    today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const route = onboardingScheduleRoute(
    props.access.companyId,
    employeeId,
    parameters,
    today,
    "/new",
  );
  if (route.foreignCompany) return <Navigate to={route.directory} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <OnboardingScheduleStartBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${route.asOf}`}
      {...props}
      employeeId={employeeId}
      asOf={route.asOf}
    />
  );
}
function OnboardingScheduleStartBinding(
  props: OnboardingScheduleScreenProps & { employeeId: string; asOf: string },
) {
  const [parameters] = useSearchParams(),
    route = onboardingScheduleRoute(
      props.access.companyId,
      props.employeeId,
      parameters,
      props.asOf,
    );
  const controller = useMemo(
    () =>
      new OnboardingScheduleStartController(
        props.loadEmployee,
        props.lifecycle,
        props.access,
        props.employeeId,
        props.asOf,
        props.nextIdentifier,
      ),
    [
      props.loadEmployee,
      props.lifecycle,
      props.access,
      props.employeeId,
      props.asOf,
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
  const form = useOnboardingScheduleForm(controller, state);
  const view = useMemo(
    () => (state.review ? onboardingScheduleReviewView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  const rows = useMemo(
    () =>
      state.review
        ? onboardingTaskRows(
            state.review.template,
            state.review.targetDate,
            Object.fromEntries(
              Object.entries(form.assignees).map(([key, value]) => [key, value.displayName]),
            ),
            props.locale,
          )
        : [],
    [state.review, form.assignees, props.locale],
  );
  const canSubmit = state.review !== null && canScheduleOnboarding(props.access, state.review);
  const selectedAssignee =
    form.picker?.kind === "assignee" && Object.hasOwn(form.assignees, form.picker.key)
      ? (form.assignees[form.picker.key] ?? null)
      : null;
  return (
    <>
      <OnboardingScheduleStartPage
        state={state}
        form={form}
        view={view}
        rows={rows}
        canSubmit={canSubmit}
        companyName={props.companyName}
        locale={props.locale}
        backTo={route.listTo}
        successTo={
          state.scheduleId
            ? `${route.path}/${encodeURIComponent(state.scheduleId)}?${route.query}`
            : null
        }
        onRetry={controller.retry}
      />
      {form.picker?.kind === "template" && state.stage === "editing" && (
        <LifecycleTemplatePicker
          accountId={props.accountId}
          access={props.access}
          load={props.lifecycle.loadTemplates}
          kind="ONBOARDING"
          locale={props.locale}
          onSelect={form.selectTemplate}
          onClose={form.closePicker}
        />
      )}
      {form.picker?.kind === "assignee" && state.stage === "reviewing" && canSubmit && (
        <LifecycleAssigneePicker
          accountId={props.accountId}
          access={props.access}
          load={props.lifecycle.loadAssignees}
          excluded={selectedAssignee?.id ?? null}
          locale={props.locale}
          onSelect={form.selectAssignee}
          onClose={form.closePicker}
          onClear={selectedAssignee ? form.clearAssignee : undefined}
        />
      )}
    </>
  );
}
