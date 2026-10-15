import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canResumeLeaveCarryExpiry } from "../../domain/policies/leave-carry-expiry-policy";
import type { LeaveCarryExpiryScreenProps } from "../contracts/leave-carry-expiry-screen-props";
import { LeaveCarryExpiryResumeController } from "../controllers/leave-carry-expiry-resume-controller";
import { useLeaveReasonForm } from "../controllers/use-leave-reason-form";
import { leaveCarryExpiryRoute } from "../models/leave-carry-expiry-route";
import { leaveCarryExpiryView } from "../models/leave-carry-expiry-view";
import { LeaveCarryExpiryResumePage } from "../pages/leave-carry-expiry-resume-page";
import { useLeaveCarryVisibility } from "./use-leave-carry-visibility";
export function LeaveCarryExpiryResumeScreen(props: LeaveCarryExpiryScreenProps) {
  const { expiryId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = leaveCarryExpiryRoute(
    props.access.companyId,
    parameters,
    `/${encodeURIComponent(expiryId)}/resume`,
  );
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveCarryExpiryResumeBinding
      key={`${props.accountId}:${props.access.companyId}:${expiryId}`}
      {...props}
      id={expiryId}
    />
  );
}
function LeaveCarryExpiryResumeBinding(props: LeaveCarryExpiryScreenProps & { id: string }) {
  const [parameters] = useSearchParams(),
    route = leaveCarryExpiryRoute(props.access.companyId, parameters);
  const controller = useMemo(
    () =>
      new LeaveCarryExpiryResumeController(
        props.leave.loadCarryExpiry,
        props.leave.resumeCarryExpiry,
        props.access,
        props.id,
        props.nextIdentifier,
      ),
    [
      props.leave.loadCarryExpiry,
      props.leave.resumeCarryExpiry,
      props.access,
      props.id,
      props.nextIdentifier,
    ],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useLeaveCarryVisibility(controller);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const form = useLeaveReasonForm(controller, state);
  const view = useMemo(
    () => (state.review ? leaveCarryExpiryView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  return (
    <LeaveCarryExpiryResumePage
      state={state}
      form={form}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      onRetry={controller.retry}
      canSubmit={state.review !== null && canResumeLeaveCarryExpiry(props.access, state.review)}
      backTo={`${route.path}/${encodeURIComponent(props.id)}?${route.detailQuery}`}
      successTo={state.expiryId ? `${route.path}/${state.expiryId}?${route.detailQuery}` : null}
    />
  );
}
