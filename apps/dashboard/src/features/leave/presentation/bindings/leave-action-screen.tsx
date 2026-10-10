import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type LeaveRequestIntent,
  leaveRequestIntents,
} from "../../domain/entities/leave-request-action";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveActionController } from "../controllers/leave-action-controller";
import { useLeaveActionForm } from "../controllers/use-leave-action-form";
import { leaveActionCopy, leaveActionView } from "../models/leave-action-view";
import { leaveParameters, leaveQuery } from "../models/leave-route";
import { LeaveActionPage } from "../pages/leave-action-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function LeaveActionScreen(props: Props) {
  const { requestId = "", intent = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = leaveParameters(
    props.access.companyId,
    leaveQuery(parameters, props.access.companyId),
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/leave/requests?${query}`} replace />;
  if (!leaveRequestIntents.includes(intent as LeaveRequestIntent))
    return <Navigate to={`/leave/requests/${encodeURIComponent(requestId)}?${query}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/leave/requests/${encodeURIComponent(requestId)}/${intent}?${query}`}
        replace
      />
    );
  return (
    <LeaveActionBinding
      key={`${props.accountId}:${props.access.companyId}:${requestId}:${intent}`}
      {...props}
      id={requestId}
      intent={intent as LeaveRequestIntent}
      backTo={`/leave/requests/${encodeURIComponent(requestId)}?${query}`}
    />
  );
}
function LeaveActionBinding(
  props: Props & { id: string; intent: LeaveRequestIntent; backTo: string },
) {
  const { leave, access, id, intent, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () => new LeaveActionController(leave, access, id, intent, nextIdentifier),
    [leave, access, id, intent, nextIdentifier],
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
  const form = useLeaveActionForm(controller, state);
  const view = useMemo(
    () => (state.review ? leaveActionView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  return (
    <LeaveActionPage
      state={state}
      form={form}
      view={view}
      copy={leaveActionCopy(intent, state.phase, locale)}
      companyName={props.companyName}
      locale={locale}
      timezone={timezone}
      backTo={props.backTo}
      onRetry={controller.retry}
    />
  );
}
