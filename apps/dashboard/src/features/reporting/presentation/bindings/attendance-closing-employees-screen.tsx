import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceClosingEmployeeQuery } from "../../domain/entities/attendance-closing-report";
import { isAttendanceClosingReportRange } from "../../domain/policies/attendance-closing-report-policy";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { AttendanceClosingEmployeesController } from "../controllers/attendance-closing-employees-controller";
import { attendanceClosingEmployeesView } from "../models/attendance-closing-report-view";
import { AttendanceClosingEmployeesPage } from "../pages/attendance-closing-employees-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadAttendanceClosingEmployees: ReportingUseCases["loadAttendanceClosingEmployees"];
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function AttendanceClosingEmployeesScreen(props: Props) {
  const [params, setParams] = useSearchParams();
  const month = params.get("month") ?? companyDate(props.timezone, new Date()).slice(0, 7);
  const from = params.get("from") ?? month,
    until = params.get("until") ?? month;
  const query = useMemo(() => ({ month, after: params.get("after") }), [month, params]);
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate replace to="/reports/attendance-closings" />;
  if (!params.has("company"))
    return (
      <Navigate
        replace
        to={`/reports/attendance-closings/employees?${new URLSearchParams({ company: props.access.companyId, month, from, until })}`}
      />
    );
  const origin =
    isAttendanceClosingReportRange(from, until) && month >= from && month <= until
      ? { from, until }
      : { from: month, until: month };
  return (
    <Binding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      from={origin.from}
      until={origin.until}
      onPage={(after) => {
        const next = new URLSearchParams({ company: props.access.companyId, month, ...origin });
        if (after !== null) next.set("after", after);
        setParams(next);
      }}
    />
  );
}
function Binding(
  props: Props & {
    query: AttendanceClosingEmployeeQuery;
    from: string;
    until: string;
    onPage: (after: string | null) => void;
  },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new AttendanceClosingEmployeesController(
        props.loadAttendanceClosingEmployees,
        props.access,
        props.query,
      ),
    [props.loadAttendanceClosingEmployees, props.access, props.query],
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
  const rows = useMemo(
    () => (state.page ? attendanceClosingEmployeesView(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  return (
    <AttendanceClosingEmployeesPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      month={props.query.month}
      backTo={`/reports/attendance-closings?${new URLSearchParams({ company: props.access.companyId, from: props.from, until: props.until })}`}
      firstPage={props.query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => props.onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) props.onPage(state.page.nextCursor);
      }}
      onOpen={(id) => {
        const row = state.page?.items.find((row) => row.employeeId === id);
        if (!row) return;
        const params = new URLSearchParams({
          company: props.access.companyId,
          reportFrom: props.from,
          reportUntil: props.until,
        });
        if (props.query.after !== null) params.set("reportAfter", props.query.after);
        const base = `/workforce/periods/${encodeURIComponent(props.query.month)}/employees/${encodeURIComponent(row.employeeId)}`;
        navigate(
          `${base}${row.adjustment ? `/adjustments/${encodeURIComponent(row.adjustment.id)}` : ""}?${params}`,
        );
      }}
    />
  );
}
