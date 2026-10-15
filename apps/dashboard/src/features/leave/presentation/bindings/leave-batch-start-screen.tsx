import { useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { LeaveBatchKind } from "../../domain/entities/leave-batch";
import type { LeaveBatchScreenProps } from "../contracts/leave-batch-screen-props";
import { LeaveBatchStartController } from "../controllers/leave-batch-start-controller";
import { useLeaveBatchForm } from "../controllers/use-leave-batch-form";
import { leaveBatchRoute } from "../models/leave-batch-route";
import { leaveBatchReviewView } from "../models/leave-batch-view";
import { LeaveBatchStartPage } from "../pages/leave-batch-start-page";
import { LeaveBatchTypePicker } from "./leave-batch-type-picker";
export function LeaveBatchStartScreen(props: LeaveBatchScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters, "/new");
  if (route.foreignCompany || route.invalidKind) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveBatchStartBinding
      key={`${props.accountId}:${props.access.companyId}:${route.kind}`}
      {...props}
      kind={route.kind}
    />
  );
}
function LeaveBatchStartBinding(props: LeaveBatchScreenProps & { kind: LeaveBatchKind }) {
  const [parameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters);
  const controller = useMemo(
    () =>
      new LeaveBatchStartController(props.leave, props.access, props.kind, props.nextIdentifier),
    [props.leave, props.access, props.kind, props.nextIdentifier],
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
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const form = useLeaveBatchForm(controller, state, props.access.companyId, props.kind, today),
    formId = useId();
  const view = useMemo(
    () => (state.review ? leaveBatchReviewView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  return (
    <>
      <LeaveBatchStartPage
        state={state}
        form={form}
        view={view}
        kind={props.kind}
        locale={props.locale}
        companyName={props.companyName}
        backTo={route.listTo}
        formId={formId}
        onRetry={controller.retry}
        successTo={
          state.batchId ? `${route.path}/${encodeURIComponent(state.batchId)}?${route.query}` : null
        }
      />
      {form.pickerOpen && state.stage === "editing" && (
        <LeaveBatchTypePicker
          {...props}
          kind={props.kind}
          onSelect={form.selectType}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}
