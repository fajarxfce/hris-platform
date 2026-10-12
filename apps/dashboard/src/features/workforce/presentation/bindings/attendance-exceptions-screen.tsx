import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { AttendanceExceptionSearchInput } from "../../domain/entities/attendance-exception-search";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { useAttendanceExceptionFilters } from "../controllers/use-attendance-exception-filters";
import { useAttendanceExceptions } from "../controllers/use-attendance-exceptions";
import {
  attendanceExceptionParameters,
  attendanceExceptionSearch,
  attendanceExceptionsPath,
} from "../models/attendance-exception-route";
import { attendanceExceptionRows } from "../models/attendance-exception-view";
import { attendanceDayPath, attendancePath } from "../models/attendance-route";
import { calendarParameters } from "../models/calendar-route";
import { AttendanceExceptionsPage } from "../pages/attendance-exceptions-page";

export function AttendanceExceptionsScreen(props: WorkforceScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => attendanceExceptionSearch(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${attendanceExceptionsPath}?${attendanceExceptionParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <AttendanceExceptionsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) =>
        setParameters(attendanceExceptionParameters(props.access.companyId, next))
      }
    />
  );
}

function AttendanceExceptionsBinding(
  props: WorkforceScreenProps & {
    search: AttendanceExceptionSearchInput;
    onSearch: (search: AttendanceExceptionSearchInput) => void;
  },
) {
  const navigate = useNavigate();
  const state = useAttendanceExceptions(
    props.accountId,
    props.access,
    props.search,
    props.workforce.loadAttendanceExceptions,
  );
  const filters = useAttendanceExceptionFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? attendanceExceptionRows(state.page, props.locale, props.timezone) : []),
    [state.page, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <AttendanceExceptionsPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      backTo={attendancePath}
      firstPage={props.search.after === null}
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        const item = state.page?.items.find(
          (entry) => `${entry.employeeId}:${entry.workDate}` === id,
        );
        if (item)
          navigate(
            `${attendanceDayPath(item.employeeId, item.workDate)}?${calendarParameters(props.access.companyId, props.search)}`,
          );
      }}
    />
  );
}
