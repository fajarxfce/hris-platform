import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canResumeLeaveCarryExpiry } from "../../domain/policies/leave-carry-expiry-policy";
import type { LeaveCarryExpiryScreenProps } from "../contracts/leave-carry-expiry-screen-props";
import { LeaveCarryExpiryController } from "../controllers/leave-carry-expiry-controller";
import { leaveCarryExpiryRoute } from "../models/leave-carry-expiry-route";
import { leaveCarryExpiryView } from "../models/leave-carry-expiry-view";
import { LeaveCarryExpiryPage } from "../pages/leave-carry-expiry-page";
import { useLeaveCarryVisibility } from "./use-leave-carry-visibility";
export function LeaveCarryExpiryScreen(props: LeaveCarryExpiryScreenProps) {
  const { expiryId = "" } = useParams(),
    [parameters] = useSearchParams();
  const route = leaveCarryExpiryRoute(
    props.access.companyId,
    parameters,
    `/${encodeURIComponent(expiryId)}`,
  );
  if (route.foreignCompany) return <Navigate to={route.listTo} replace />;
  if (!route.canonical) return <Navigate to={route.current} replace />;
  return (
    <LeaveCarryExpiryBinding
      key={`${props.accountId}:${props.access.companyId}:${expiryId}:${route.resultAfter}`}
      {...props}
      id={expiryId}
      after={route.resultAfter}
    />
  );
}
function LeaveCarryExpiryBinding(
  props: LeaveCarryExpiryScreenProps & { id: string; after: string | null },
) {
  const [parameters, setParameters] = useSearchParams(),
    route = leaveCarryExpiryRoute(props.access.companyId, parameters);
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new LeaveCarryExpiryController(
        props.leave.loadCarryExpiry,
        props.access,
        props.id,
        props.after,
      ),
    [props.leave.loadCarryExpiry, props.access, props.id, props.after],
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
  const view = useMemo(
    () => (state.details ? leaveCarryExpiryView(state.details, props.locale) : null),
    [state.details, props.locale],
  );
  const current =
    ["ready", "updating"].includes(state.stage) && !state.failure ? state.details : null;
  return (
    <LeaveCarryExpiryPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={route.listTo}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      resumeTo={
        state.stage === "ready" && current && canResumeLeaveCarryExpiry(props.access, current)
          ? `${route.path}/${encodeURIComponent(props.id)}/resume?${route.detailQuery}`
          : null
      }
      jobTo={
        current &&
        (current.expiry.actorId === props.accountId ||
          props.access.permissions.includes("jobs.read"))
          ? `/administration/jobs?${new URLSearchParams({ company: props.access.companyId, job: current.job.id })}`
          : null
      }
      closingTo={
        current?.expiry.sourceKind === "BATCH"
          ? `/leave/batches/${current.expiry.sourceId}?company=${encodeURIComponent(props.access.companyId)}`
          : null
      }
      onFirst={() => setParameters(new URLSearchParams(route.query))}
      onNext={() => {
        if (current?.results.nextCursor) {
          const query = new URLSearchParams(route.query);
          query.set("resultAfter", current.results.nextCursor);
          setParameters(query);
        }
      }}
      onLedger={(id) => {
        if (current?.results.items.some((item) => item.employee.id === id))
          void navigate(
            `/leave/employees/${encodeURIComponent(id)}/balances/${current.expiry.typeId}?${new URLSearchParams({ company: props.access.companyId, year: String(current.expiry.year) })}`,
          );
      }}
    />
  );
}
