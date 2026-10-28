import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveReportQuery } from "../../domain/entities/leave-report-request";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { LeaveReportRequestsController } from "../controllers/leave-report-requests-controller";
import { useLeaveReportFilters } from "../controllers/use-leave-report-filters";
import { leaveReportParameters, leaveReportQuery } from "../models/leave-report-route";
import { leaveReportRequestsView } from "../models/leave-report-view";
import { LeaveReportRequestsPage } from "../pages/leave-report-requests-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadLeaveRequests: ReportingUseCases["loadLeaveRequests"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function LeaveReportRequestsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = useMemo(
    () => leaveReportQuery(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/leave/requests?${leaveReportParameters(props.access.companyId, query)}`}
      />
    );
  return (
    <LeaveReportRequestsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQuery={(next) => setParameters(leaveReportParameters(props.access.companyId, next))}
    />
  );
}

function LeaveReportRequestsBinding(
  props: Props & { query: LeaveReportQuery; onQuery: (query: LeaveReportQuery) => void },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new LeaveReportRequestsController(props.loadLeaveRequests, props.access, props.query),
    [props.loadLeaveRequests, props.access, props.query],
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
  const filters = useLeaveReportFilters(
    props.query.from,
    props.query.until,
    props.locale,
    (from, until, status) => props.onQuery({ from, until, status, after: null }),
    props.query.status,
  );
  const rows = useMemo(
    () => (state.page ? leaveReportRequestsView(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  return (
    <LeaveReportRequestsPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={props.companyName}
      locale={props.locale}
      firstPage={props.query.after === null}
      backTo={`/reports/leave?${leaveReportParameters(props.access.companyId, { ...props.query, status: null, after: null })}`}
      onRefresh={controller.refresh}
      onFirst={() => props.onQuery({ ...props.query, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onQuery({ ...props.query, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((row) => row.id === id)) {
          const query = new URLSearchParams({
            company: props.access.companyId,
            reportFrom: props.query.from,
            reportUntil: props.query.until,
          });
          if (props.query.status !== null) query.set("reportStatus", props.query.status);
          if (props.query.after !== null) query.set("reportAfter", props.query.after);
          navigate(`/leave/requests/${encodeURIComponent(id)}?${query}`);
        }
      }}
    />
  );
}
