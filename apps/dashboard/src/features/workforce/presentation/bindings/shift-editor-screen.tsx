import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { ShiftEditorController } from "../controllers/shift-editor-controller";
import { useShiftForm } from "../controllers/use-shift-form";
import { shiftParameters, shiftSearch, shiftsPath } from "../models/shift-route";
import { ShiftEditorPage } from "../pages/shift-editor-page";

type Props = WorkforceScreenProps & { creating: boolean };
export function ShiftEditorScreen(props: Props) {
  const { shiftId = "" } = useParams();
  const [parameters] = useSearchParams();
  const pinned = shiftParameters(
    props.access.companyId,
    shiftSearch(parameters, props.access.companyId),
  ).toString();
  const path = props.creating
    ? `${shiftsPath}/new`
    : `${shiftsPath}/${encodeURIComponent(shiftId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${shiftsPath}?${pinned}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <ShiftEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${pinned}`}
      {...props}
      id={shiftId}
      parameters={pinned}
    />
  );
}
function ShiftEditorBinding(props: Props & { id: string; parameters: string }) {
  const { workforce, access, creating, nextIdentifier } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : props.id),
    [creating, nextIdentifier, props.id],
  );
  const controller = useMemo(
    () => new ShiftEditorController(workforce, access, creating, id, nextIdentifier),
    [workforce, access, creating, id, nextIdentifier],
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
  const form = useShiftForm(controller, state, props.timezone);
  useWorkspaceRevalidation(state.failure);
  return (
    <ShiftEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`${shiftsPath}?${props.parameters}`}
      savedTo={state.receipt ? `${shiftsPath}/${state.receipt.id}?${props.parameters}` : null}
      onRetry={controller.retrySave}
    />
  );
}
