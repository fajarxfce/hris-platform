import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkPeriodIntent } from "../../domain/entities/work-period-action-context";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useWorkPeriodActionForm } from "../controllers/use-work-period-action-form";
import { WorkPeriodActionController } from "../controllers/work-period-action-controller";
import { workPeriodActionPath, workPeriodPath, workPeriodsPath } from "../models/work-period-route";
import { workPeriodOverviewView } from "../models/work-period-view";
import { WorkPeriodActionPage } from "../pages/work-period-action-page";

export function WorkPeriodActionScreen(props: WorkforceScreenProps) {
  const { month = "", intent = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${workPeriodsPath}?${new URLSearchParams({ company: props.access.companyId })}`}
        replace
      />
    );
  if (intent !== "close" && intent !== "recover")
    return <Navigate to={workPeriodPath(props.access.companyId, month)} replace />;
  if (!parameters.has("company"))
    return <Navigate to={workPeriodActionPath(props.access.companyId, month, intent)} replace />;
  return (
    <WorkPeriodActionBinding
      key={`${props.accountId}:${props.access.companyId}:${month}:${intent}`}
      {...props}
      month={month}
      intent={intent}
    />
  );
}

function WorkPeriodActionBinding(
  props: WorkforceScreenProps & { month: string; intent: WorkPeriodIntent },
) {
  const { workforce, access, month, intent, nextIdentifier, locale } = props;
  const controller = useMemo(
    () => new WorkPeriodActionController(workforce, access, month, intent, nextIdentifier),
    [workforce, access, month, intent, nextIdentifier],
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
  const form = useWorkPeriodActionForm(controller, state);
  const view = useMemo(
    () => (state.overview ? workPeriodOverviewView(state.overview, locale) : null),
    [state.overview, locale],
  );
  return (
    <WorkPeriodActionPage
      state={state}
      form={form}
      view={view}
      intent={intent}
      companyName={props.companyName}
      locale={locale}
      backTo={workPeriodPath(access.companyId, month)}
      onRetry={controller.retry}
    />
  );
}
