import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import { canManageCalendars } from "../../domain/policies/calendar-policy";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useCalendarFilters } from "../controllers/use-calendar-filters";
import { useCalendarReview } from "../controllers/use-calendar-review";
import {
  calendarParameters,
  calendarPath,
  calendarRange,
  calendarsPath,
  workforceEmployeeParameters,
} from "../models/calendar-route";
import { calendarView } from "../models/calendar-view";
import { EmployeeCalendarPage } from "../pages/employee-calendar-page";

export function EmployeeCalendarScreen(props: WorkforceScreenProps) {
  const { employeeId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const range = useMemo(
    () => calendarRange(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${calendarsPath}?${workforceEmployeeParameters(props.access.companyId, { asOf: today, query: "", after: null })}`}
        replace
      />
    );
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${calendarPath(employeeId)}?${calendarParameters(props.access.companyId, range)}`}
        replace
      />
    );
  return (
    <EmployeeCalendarBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}`}
      {...props}
      employeeId={employeeId}
      range={range}
      onRange={(next) => setParameters(calendarParameters(props.access.companyId, next))}
      onRoster={(day) =>
        navigate(
          `${calendarPath(employeeId)}/roster/${day}?${calendarParameters(props.access.companyId, range)}`,
        )
      }
    />
  );
}
function EmployeeCalendarBinding(
  props: WorkforceScreenProps & {
    employeeId: string;
    range: CalendarRange;
    onRange: (range: CalendarRange) => void;
    onRoster: (day: string) => void;
  },
) {
  const state = useCalendarReview(
    props.accountId,
    props.access,
    props.employeeId,
    props.range,
    props.workforce.loadCalendarReview,
  );
  const filters = useCalendarFilters(props.range, props.locale, props.onRange);
  const view = useMemo(
    () => (state.review ? calendarView(state.review, props.locale) : null),
    [state.review, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  const manage = canManageCalendars(props.access.permissions);
  return (
    <EmployeeCalendarPage
      state={state}
      filters={filters}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${calendarsPath}?${workforceEmployeeParameters(props.access.companyId, { asOf: props.range.from, query: "", after: null })}`}
      weeklyTo={
        manage
          ? `${calendarPath(props.employeeId)}/schedule?${calendarParameters(props.access.companyId, props.range)}`
          : null
      }
      onRoster={manage ? props.onRoster : null}
    />
  );
}
