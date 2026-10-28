import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { AttendanceReportController } from "../controllers/attendance-report-controller";
import { useAttendanceReportFilters } from "../controllers/use-attendance-report-filters";
import { attendanceReportView } from "../models/attendance-report-view";
import { saveAppliedReportFilter } from "../models/saved-report-filter-route";
import { AttendanceReportPage } from "../pages/attendance-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadAttendanceExceptions: ReportingUseCases["loadAttendanceExceptions"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function AttendanceReportScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const changedCompany =
    parameters.has("company") && parameters.get("company") !== props.access.companyId;
  const from = changedCompany
    ? `${today.slice(0, 8)}01`
    : (parameters.get("from") ?? `${today.slice(0, 8)}01`);
  const until = changedCompany ? today : (parameters.get("until") ?? today);
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/attendance-exceptions?${new URLSearchParams({ company: props.access.companyId, from, until })}`}
      />
    );
  return (
    <AttendanceReportBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      from={from}
      until={until}
      onApply={(from, until) => setParameters({ company: props.access.companyId, from, until })}
    />
  );
}

function AttendanceReportBinding(
  props: Props & { from: string; until: string; onApply: (from: string, until: string) => void },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new AttendanceReportController(
        props.loadAttendanceExceptions,
        props.access,
        props.from,
        props.until,
      ),
    [props.loadAttendanceExceptions, props.access, props.from, props.until],
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
  const view = useMemo(
    () => (state.report ? attendanceReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = useAttendanceReportFilters(props.from, props.until, props.locale, props.onApply);
  return (
    <AttendanceReportPage
      savedFilterTo={
        state.report
          ? saveAppliedReportFilter(props.access.companyId, "ATTENDANCE_EXCEPTIONS", {
              asOf: null,
              from: props.from,
              until: props.until,
              status: null,
              companies: [],
            })
          : null
      }
      state={state}
      view={view}
      filters={filters}
      companyName={props.companyName}
      locale={props.locale}
      onRefresh={controller.refresh}
      onOpenDay={(date) => {
        if (state.report?.days.some((day) => day.date === date && day.counts.affectedDays > 0))
          navigate(
            `/workforce/attendance/exceptions?${new URLSearchParams({ company: props.access.companyId, from: date, until: date })}`,
          );
      }}
    />
  );
}
