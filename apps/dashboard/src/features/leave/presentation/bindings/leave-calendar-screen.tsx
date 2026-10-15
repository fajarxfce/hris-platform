import { useCallback, useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leaveCalendarScopes } from "../../domain/entities/leave-calendar";
import { canReadLeaveCalendar } from "../../domain/policies/leave-calendar-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveCalendarController } from "../controllers/leave-calendar-controller";
import { useLeaveCalendarMonth } from "../controllers/use-leave-calendar-month";
import { leaveCalendarMessages } from "../i18n/leave-calendar-messages";
import { leaveCalendarParameters, leaveCalendarRoute } from "../models/leave-calendar-route";
import { leaveCalendarRows, leaveCalendarView } from "../models/leave-calendar-view";
import { leaveParameters } from "../models/leave-route";
import { LeaveCalendarPage } from "../pages/leave-calendar-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  leave: LeaveUseCases;
  companyName: string;
  locale: Locale;
};
export function LeaveCalendarScreen(props: Props) {
  const [parameters] = useSearchParams(),
    route = leaveCalendarRoute(props.access.companyId, props.access.permissions, parameters);
  if (route.foreignCompany || !route.canonical) return <Navigate to={route.to} replace />;
  return <LeaveCalendarBinding key={`${props.accountId}:${props.access.companyId}`} {...props} />;
}
function LeaveCalendarBinding(props: Props) {
  const [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const route = leaveCalendarRoute(props.access.companyId, props.access.permissions, parameters);
  const controller = useMemo(
    () =>
      new LeaveCalendarController(
        props.leave.loadCalendar,
        props.leave.loadCalendarEntries,
        props.access,
        route.scope,
        route.month,
      ),
    [
      props.leave.loadCalendar,
      props.leave.loadCalendarEntries,
      props.access,
      route.scope,
      route.month,
    ],
  );
  const visible = useContext(WorkspaceVisibilityContext);
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(
    () => controller.selectEntries(route.date, route.after),
    [controller, route.date, route.after],
  );
  useEffect(() => {
    if (visible) controller.activate();
    return controller.deactivate;
  }, [controller, visible]);
  useWorkspaceRevalidation(state.failure ?? state.entriesFailure);
  const view = useMemo(
    () => (state.calendar ? leaveCalendarView(state.calendar, state.date, props.locale) : null),
    [state.calendar, state.date, props.locale],
  );
  const rows = useMemo(
    () => leaveCalendarRows(state.entries?.entries.items ?? [], props.locale),
    [state.entries, props.locale],
  );
  const selectMonth = useCallback(
    (month: string | null) => {
      if (route.scope)
        setParameters(leaveCalendarParameters(props.access.companyId, route.scope, month));
    },
    [props.access.companyId, route.scope, setParameters],
  );
  const form = useLeaveCalendarMonth(
    state.calendar?.month ?? route.month ?? "",
    props.locale,
    selectMonth,
  );
  const text = leaveCalendarMessages(props.locale),
    scopes = leaveCalendarScopes
      .filter((scope) => canReadLeaveCalendar(props.access.permissions, scope))
      .map((scope) => ({ value: scope, label: scope === "COMPANY" ? text.company : text.team }));
  return (
    <LeaveCalendarPage
      state={state}
      view={view}
      rows={rows}
      scope={route.scope}
      scopes={scopes}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      onRefresh={controller.refresh}
      onScope={(scope) => {
        if (scope !== "COMPANY" && scope !== "TEAM") return;
        setParameters(leaveCalendarParameters(props.access.companyId, scope, route.month));
      }}
      onPrevious={() => {
        if (view?.previousMonth) selectMonth(view.previousMonth);
      }}
      onNext={() => {
        if (view?.nextMonth) selectMonth(view.nextMonth);
      }}
      onToday={() => {
        selectMonth(null);
        if (route.month === null) void controller.refresh();
      }}
      onDate={(date) => {
        if (route.scope)
          setParameters(
            leaveCalendarParameters(props.access.companyId, route.scope, route.month, date),
          );
      }}
      onFirstPage={() => {
        if (route.scope && state.date)
          setParameters(
            leaveCalendarParameters(props.access.companyId, route.scope, route.month, state.date),
          );
      }}
      onNextPage={() => {
        if (route.scope && state.date && state.entries?.entries.nextCursor)
          setParameters(
            leaveCalendarParameters(
              props.access.companyId,
              route.scope,
              route.month,
              state.date,
              state.entries.entries.nextCursor,
            ),
          );
      }}
      onOpen={(id) => {
        const row = state.entries?.entries.items.find((item) => item.requestId === id);
        if (row && state.calendar && state.date)
          void navigate(
            `/leave/requests/${encodeURIComponent(row.requestId)}?${leaveParameters(
              props.access.companyId,
              { employeeId: row.employeeId, status: null, after: null },
              null,
              {
                scope: state.calendar.scope,
                month: state.calendar.month,
                date: state.date,
                after: state.after,
              },
            )}`,
          );
      }}
    />
  );
}
