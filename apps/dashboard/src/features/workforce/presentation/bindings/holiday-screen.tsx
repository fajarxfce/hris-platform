import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canManageHolidays } from "../../domain/policies/holiday-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { HolidayController } from "../controllers/holiday-controller";
import { holidayParameters, holidayRange, holidaysPath } from "../models/holiday-route";
import { holidayView } from "../models/holiday-view";
import { HolidayPage } from "../pages/holiday-page";

export function HolidayScreen(props: WorkforceScreenProps) {
  const { holidayId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const pinned = holidayParameters(
    props.access.companyId,
    holidayRange(parameters, props.access.companyId, today),
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`${holidaysPath}?${pinned}`} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${holidaysPath}/${encodeURIComponent(holidayId)}?${pinned}`} replace />;
  return (
    <HolidayBinding
      key={`${props.accountId}:${props.access.companyId}:${holidayId}`}
      {...props}
      id={holidayId}
      parameters={pinned}
    />
  );
}
function HolidayBinding(props: WorkforceScreenProps & { id: string; parameters: string }) {
  const { workforce, access, id, locale } = props;
  const controller = useMemo(
    () => new HolidayController(workforce.loadHoliday, access, id),
    [workforce.loadHoliday, access, id],
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
    () => (state.holiday ? holidayView(state.holiday, locale) : null),
    [state.holiday, locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <HolidayPage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={locale}
      backTo={`${holidaysPath}?${props.parameters}`}
      onRefresh={controller.refresh}
      editTo={
        state.holiday && canManageHolidays(access.permissions)
          ? `${holidaysPath}/${state.holiday.id}/edit?${props.parameters}`
          : null
      }
    />
  );
}
