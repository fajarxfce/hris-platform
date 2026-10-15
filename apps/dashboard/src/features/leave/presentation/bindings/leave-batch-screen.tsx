import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canResumeLeaveBatch } from "../../domain/policies/leave-batch-policy";
import type { LeaveBatchScreenProps } from "../contracts/leave-batch-screen-props";
import { LeaveBatchController } from "../controllers/leave-batch-controller";
import { leaveAccrualScheduleMessages } from "../i18n/leave-accrual-schedule-messages";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import { leaveBatchRoute } from "../models/leave-batch-route";
import { leaveBatchView } from "../models/leave-batch-view";
import { LeaveBatchPage } from "../pages/leave-batch-page";
export function LeaveBatchScreen(props: LeaveBatchScreenProps) {
  const { batchId = "" } = useParams(),
    [parameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters, `/${encodeURIComponent(batchId)}`);
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveBatchBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}:${route.resultAfter}`}
      {...props}
      id={batchId}
      after={route.resultAfter}
    />
  );
}
function LeaveBatchBinding(props: LeaveBatchScreenProps & { id: string; after: string | null }) {
  const [parameters, setParameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters),
    navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new LeaveBatchController(props.leave.loadBatch, props.access, props.id, props.after),
    [props.leave.loadBatch, props.access, props.id, props.after],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const changed = () => controller.setVisible(visible && document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, [controller, visible]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.details ? leaveBatchView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  const current =
    ["ready", "updating"].includes(state.stage) && !state.failure ? state.details : null;
  return (
    <LeaveBatchPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={route.backTo}
      backLabel={
        route.schedule
          ? leaveAccrualScheduleMessages(props.locale).backToDetail
          : leaveBatchMessages(props.locale).back
      }
      resumeTo={
        state.stage === "ready" && current && canResumeLeaveBatch(props.access, current)
          ? `${route.path}/${encodeURIComponent(props.id)}/resume?${route.detailQuery}`
          : null
      }
      jobTo={
        current &&
        (current.batch.actorId === props.accountId ||
          props.access.permissions.includes("jobs.read"))
          ? `/administration/jobs?${new URLSearchParams({ company: props.access.companyId, job: current.job.id })}`
          : null
      }
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters(new URLSearchParams(route.resultQuery))}
      onNext={() => {
        if (current?.results.nextCursor) {
          const query = new URLSearchParams(route.resultQuery);
          query.set("resultAfter", current.results.nextCursor);
          setParameters(query);
        }
      }}
      onLedger={(id) => {
        if (current?.results.items.some((item) => item.employee.id === id))
          void navigate(
            `/leave/employees/${encodeURIComponent(id)}/balances/${encodeURIComponent(current.batch.typeId)}?${new URLSearchParams({ company: props.access.companyId, year: current.batch.period.slice(0, 4) })}`,
          );
      }}
    />
  );
}
