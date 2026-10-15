import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canResumeLeaveBatch } from "../../domain/policies/leave-batch-policy";
import type { LeaveBatchScreenProps } from "../contracts/leave-batch-screen-props";
import { LeaveBatchResumeController } from "../controllers/leave-batch-resume-controller";
import { useLeaveReasonForm } from "../controllers/use-leave-reason-form";
import { leaveBatchRoute } from "../models/leave-batch-route";
import { leaveBatchView } from "../models/leave-batch-view";
import { LeaveBatchResumePage } from "../pages/leave-batch-resume-page";
export function LeaveBatchResumeScreen(props: LeaveBatchScreenProps) {
  const { batchId = "" } = useParams(),
    [parameters] = useSearchParams(),
    route = leaveBatchRoute(
      props.access.companyId,
      parameters,
      `/${encodeURIComponent(batchId)}/resume`,
    );
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveBatchResumeBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}`}
      {...props}
      id={batchId}
    />
  );
}
function LeaveBatchResumeBinding(props: LeaveBatchScreenProps & { id: string }) {
  const [parameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters);
  const controller = useMemo(
    () =>
      new LeaveBatchResumeController(
        props.leave.loadBatch,
        props.leave.resumeBatch,
        props.access,
        props.id,
        props.nextIdentifier,
      ),
    [props.leave.loadBatch, props.leave.resumeBatch, props.access, props.id, props.nextIdentifier],
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
  const form = useLeaveReasonForm(controller, state),
    view = useMemo(
      () => (state.review ? leaveBatchView(state.review, props.locale) : null),
      [state.review, props.locale],
    );
  return (
    <LeaveBatchResumePage
      state={state}
      form={form}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      canSubmit={state.review !== null && canResumeLeaveBatch(props.access, state.review)}
      onRetry={controller.retry}
      backTo={`${route.path}/${encodeURIComponent(props.id)}?${route.detailQuery}`}
      successTo={
        state.batchId
          ? `${route.path}/${encodeURIComponent(state.batchId)}?${route.detailQuery}`
          : null
      }
    />
  );
}
