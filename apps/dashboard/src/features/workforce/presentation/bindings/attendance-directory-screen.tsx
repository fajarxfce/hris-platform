import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceEmployeeSearch } from "../../domain/entities/workforce-employee-search";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useWorkforceEmployeeFilters } from "../controllers/use-workforce-employee-filters";
import { useWorkforceEmployees } from "../controllers/use-workforce-employees";
import { attendanceMessages } from "../i18n/attendance-messages";
import { attendancePath, employeeAttendancePath } from "../models/attendance-route";
import {
  calendarParameters,
  calendarWeekFrom,
  workforceEmployeeParameters,
  workforceEmployeeSearch,
} from "../models/calendar-route";
import { workforceEmployeeView } from "../models/calendar-view";
import { WorkforceEmployeesPage } from "../pages/workforce-employees-page";

export function AttendanceDirectoryScreen(props: WorkforceScreenProps) {
  const [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => workforceEmployeeSearch(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${attendancePath}?${workforceEmployeeParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <AttendanceDirectoryBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(workforceEmployeeParameters(props.access.companyId, next))}
      onOpen={(id) =>
        navigate(
          employeeAttendancePath(id) +
            "?" +
            calendarParameters(props.access.companyId, calendarWeekFrom(search.asOf)),
        )
      }
    />
  );
}
function AttendanceDirectoryBinding(
  props: WorkforceScreenProps & {
    search: WorkforceEmployeeSearch;
    onSearch: (search: WorkforceEmployeeSearch) => void;
    onOpen: (id: string) => void;
  },
) {
  const state = useWorkforceEmployees(
    props.accountId,
    props.access,
    props.search,
    props.workforce.loadWorkforceEmployees,
  );
  const filters = useWorkforceEmployeeFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? workforceEmployeeView(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <WorkforceEmployeesPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      labels={attendanceMessages(props.locale)}
      firstPage={props.search.after === null}
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={props.onOpen}
    />
  );
}
