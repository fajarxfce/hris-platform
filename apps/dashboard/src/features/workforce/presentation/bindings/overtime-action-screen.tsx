import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { OvertimeActionController } from "../controllers/overtime-action-controller";
import { useOvertimeActionForm } from "../controllers/use-overtime-action-form";
import {
  type OvertimeActionIntent,
  overtimeActionIntents,
  overtimeIntentAction,
} from "../models/overtime-action-intent";
import { overtimeActionCopy } from "../models/overtime-action-view";
import {
  overtimeParameters,
  overtimePath,
  overtimeRequestPath,
  overtimeSearchFromRoute,
} from "../models/overtime-route";
import { overtimeDetailsView } from "../models/overtime-view";
import { OvertimeActionPage } from "../pages/overtime-action-page";

export function OvertimeActionScreen(props: WorkforceScreenProps) {
  const { overtimeId = "", intent = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = overtimeParameters(
    props.access.companyId,
    overtimeSearchFromRoute(parameters, props.access.companyId, today),
  );
  const backTo = `${overtimeRequestPath(overtimeId)}?${query}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${overtimePath}?${query}`} replace />;
  if (!overtimeActionIntents.includes(intent as OvertimeActionIntent))
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${overtimeRequestPath(overtimeId)}/${intent}?${query}`} replace />;
  return (
    <OvertimeActionBinding
      key={`${props.accountId}:${props.access.companyId}:${overtimeId}:${intent}`}
      {...props}
      id={overtimeId}
      intent={intent as OvertimeActionIntent}
      backTo={backTo}
    />
  );
}

function OvertimeActionBinding(
  props: WorkforceScreenProps & { id: string; intent: OvertimeActionIntent; backTo: string },
) {
  const { workforce, access, id, intent, nextIdentifier, locale } = props;
  const controller = useMemo(
    () =>
      new OvertimeActionController(
        workforce,
        access,
        id,
        overtimeIntentAction[intent],
        nextIdentifier,
      ),
    [workforce, access, id, intent, nextIdentifier],
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
  const form = useOvertimeActionForm(controller, state);
  const view = useMemo(
    () => (state.review ? overtimeDetailsView(state.review, null, locale) : null),
    [state.review, locale],
  );
  return (
    <OvertimeActionPage
      state={state}
      form={form}
      view={view}
      copy={overtimeActionCopy(intent, locale)}
      actual={intent === "actual"}
      companyName={props.companyName}
      locale={locale}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
