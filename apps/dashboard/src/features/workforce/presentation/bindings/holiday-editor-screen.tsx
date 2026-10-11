import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { HolidayEditorController } from "../controllers/holiday-editor-controller";
import { useHolidayForm } from "../controllers/use-holiday-form";
import { holidayParameters, holidayRange, holidaysPath } from "../models/holiday-route";
import { HolidayEditorPage } from "../pages/holiday-editor-page";

type Props = WorkforceScreenProps & { creating: boolean };
export function HolidayEditorScreen(props: Props) {
  const { holidayId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const pinned = holidayParameters(
    props.access.companyId,
    holidayRange(parameters, props.access.companyId, today),
  ).toString();
  const path = props.creating
    ? `${holidaysPath}/new`
    : `${holidaysPath}/${encodeURIComponent(holidayId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${holidaysPath}?${pinned}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <HolidayEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${pinned}`}
      {...props}
      id={holidayId}
      parameters={pinned}
      today={today}
    />
  );
}
function HolidayEditorBinding(props: Props & { id: string; parameters: string; today: string }) {
  const { workforce, access, creating, nextIdentifier } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : props.id),
    [creating, nextIdentifier, props.id],
  );
  const controller = useMemo(
    () => new HolidayEditorController(workforce, access, creating, id, nextIdentifier),
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
  const form = useHolidayForm(controller, state, props.today);
  useWorkspaceRevalidation(state.failure);
  return (
    <HolidayEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`${holidaysPath}?${props.parameters}`}
      savedTo={state.receipt ? `${holidaysPath}/${state.receipt.id}?${props.parameters}` : null}
      onRetry={controller.retrySave}
    />
  );
}
