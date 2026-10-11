import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canManageShifts } from "../../domain/policies/shift-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { ShiftController } from "../controllers/shift-controller";
import { shiftParameters, shiftSearch, shiftsPath } from "../models/shift-route";
import { shiftView } from "../models/shift-view";
import { ShiftPage } from "../pages/shift-page";

export function ShiftScreen(props: WorkforceScreenProps) {
  const { shiftId = "" } = useParams();
  const [parameters] = useSearchParams();
  const pinned = shiftParameters(
    props.access.companyId,
    shiftSearch(parameters, props.access.companyId),
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${shiftsPath}?${pinned}`} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${shiftsPath}/${encodeURIComponent(shiftId)}?${pinned}`} replace />;
  return (
    <ShiftBinding
      key={`${props.accountId}:${props.access.companyId}:${shiftId}`}
      {...props}
      id={shiftId}
      parameters={pinned}
    />
  );
}
function ShiftBinding(props: WorkforceScreenProps & { id: string; parameters: string }) {
  const { workforce, access, id, locale } = props;
  const controller = useMemo(
    () => new ShiftController(workforce.loadShift, access, id),
    [workforce.loadShift, access, id],
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
  const view = useMemo(
    () => (state.shift ? shiftView(state.shift, locale) : null),
    [state.shift, locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <ShiftPage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={locale}
      backTo={`${shiftsPath}?${props.parameters}`}
      onRefresh={controller.refresh}
      editTo={
        state.shift && canManageShifts(access.permissions)
          ? `${shiftsPath}/${state.shift.id}/edit?${props.parameters}`
          : null
      }
    />
  );
}
