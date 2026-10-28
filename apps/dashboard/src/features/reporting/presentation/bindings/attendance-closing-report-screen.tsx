import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { AttendanceClosingReportController } from "../controllers/attendance-closing-report-controller";
import { useAttendanceClosingReportFilters } from "../controllers/use-attendance-closing-report-filters";
import { attendanceClosingReportView } from "../models/attendance-closing-report-view";
import { saveAppliedReportFilter } from "../models/saved-report-filter-route";
import { AttendanceClosingReportPage } from "../pages/attendance-closing-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadAttendanceClosings: ReportingUseCases["loadAttendanceClosings"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function AttendanceClosingReportScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const changedCompany =
    parameters.has("company") && parameters.get("company") !== props.access.companyId;
  const from = changedCompany
    ? `${today.slice(0, 4)}-01`
    : (parameters.get("from") ?? `${today.slice(0, 4)}-01`);
  const until = changedCompany ? today : (parameters.get("until") ?? today);
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/attendance-closings?${new URLSearchParams({ company: props.access.companyId, from, until })}`}
      />
    );
  return (
    <AttendanceClosingReportBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      from={from}
      until={until}
      onApply={(from, until) =>
        setParameters({
          company: props.access.companyId,
          from,
          until,
        })
      }
    />
  );
}

function AttendanceClosingReportBinding(
  props: Props & {
    from: string;
    until: string;
    onApply: (from: string, until: string) => void;
  },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new AttendanceClosingReportController(
        props.loadAttendanceClosings,
        props.access,
        props.from,
        props.until,
      ),
    [props.loadAttendanceClosings, props.access, props.from, props.until],
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
    () => (state.report ? attendanceClosingReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = useAttendanceClosingReportFilters(
    props.from,
    props.until,
    props.locale,
    props.onApply,
  );
  return (
    <AttendanceClosingReportPage
      savedFilterTo={
        state.report
          ? saveAppliedReportFilter(props.access.companyId, "ATTENDANCE_CLOSINGS", {
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
      onOpenMonth={(month) => {
        const row = state.report?.months.find((value) => value.month === month);
        if (row && row.counts.employments > 0)
          navigate(
            `/reports/attendance-closings/employees?${new URLSearchParams({
              company: props.access.companyId,
              month: row.month,
              from: props.from,
              until: props.until,
            })}`,
          );
      }}
    />
  );
}
