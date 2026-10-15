import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canRunLeaveBatch } from "../../domain/policies/leave-batch-policy";
import type { LeaveBatchScreenProps } from "../contracts/leave-batch-screen-props";
import { LeaveBatchesController } from "../controllers/leave-batches-controller";
import { leaveBatchRoute } from "../models/leave-batch-route";
import { leaveBatchesView } from "../models/leave-batch-view";
import { LeaveBatchesPage } from "../pages/leave-batches-page";
export function LeaveBatchesScreen(props: LeaveBatchScreenProps) {
  const [parameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters);
  if (route.foreignCompany || !route.canonical) return <Navigate to={route.listTo} replace />;
  return (
    <LeaveBatchesBinding
      key={`${props.accountId}:${props.access.companyId}:${route.after}`}
      {...props}
      after={route.after}
    />
  );
}
function LeaveBatchesBinding(props: LeaveBatchScreenProps & { after: string | null }) {
  const [parameters, setParameters] = useSearchParams(),
    route = leaveBatchRoute(props.access.companyId, parameters),
    navigate = useNavigate();
  const controller = useMemo(
    () => new LeaveBatchesController(props.leave.loadBatches, props.access, props.after),
    [props.leave.loadBatches, props.access, props.after],
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
  const rows = useMemo(
    () => leaveBatchesView(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <LeaveBatchesPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      accrualTo={canRunLeaveBatch(props.access.permissions, "ACCRUAL") ? route.newAccrualTo : null}
      closeTo={
        canRunLeaveBatch(props.access.permissions, "YEAR_CLOSE") ? route.newYearCloseTo : null
      }
      schedulesTo={`/leave/accrual-schedules?company=${encodeURIComponent(props.access.companyId)}`}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters({ company: props.access.companyId })}
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({ company: props.access.companyId, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((item) => item.id === id))
          void navigate(`${route.path}/${encodeURIComponent(id)}?${route.query}`);
      }}
    />
  );
}
