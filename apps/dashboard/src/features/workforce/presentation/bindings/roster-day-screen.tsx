import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { RosterDayController } from "../controllers/roster-day-controller";
import { useRosterDayForm } from "../controllers/use-roster-day-form";
import { useShiftCatalog } from "../controllers/use-shift-catalog";
import {
  calendarParameters,
  calendarPath,
  calendarRange,
  calendarsPath,
} from "../models/calendar-route";
import { RosterDayPage } from "../pages/roster-day-page";

export function RosterDayScreen(props: WorkforceScreenProps) {
  const { employeeId = "", workDate = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const range = calendarRange(parameters, props.access.companyId, today);
  const pinned = calendarParameters(props.access.companyId, range).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={calendarsPath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${calendarPath(employeeId)}/roster/${encodeURIComponent(workDate)}?${pinned}`}
        replace
      />
    );
  return (
    <RosterDayBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${workDate}:${pinned}`}
      {...props}
      employeeId={employeeId}
      date={workDate}
      backTo={`${calendarPath(employeeId)}?${pinned}`}
    />
  );
}
function RosterDayBinding(
  props: WorkforceScreenProps & { employeeId: string; date: string; backTo: string },
) {
  const { workforce, access, employeeId, date, nextIdentifier } = props;
  const controller = useMemo(
    () => new RosterDayController(workforce, access, employeeId, date, nextIdentifier),
    [workforce, access, employeeId, date, nextIdentifier],
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
  const catalog = useShiftCatalog(
    props.accountId,
    access,
    workforce.loadShifts,
    state.review !== null,
  );
  const form = useRosterDayForm(controller, state, catalog.shifts, props.locale, catalog.refresh);
  useWorkspaceRevalidation(state.failure);
  useWorkspaceRevalidation(catalog.failure);
  return (
    <RosterDayPage
      state={state}
      form={form}
      catalog={catalog}
      locale={props.locale}
      companyName={props.companyName}
      date={date}
      backTo={props.backTo}
      onRetry={controller.retrySave}
    />
  );
}
