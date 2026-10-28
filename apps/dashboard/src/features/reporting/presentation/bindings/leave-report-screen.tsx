import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { LeaveReportController } from "../controllers/leave-report-controller";
import { useLeaveReportFilters } from "../controllers/use-leave-report-filters";
import { leaveReportView } from "../models/leave-report-view";
import { LeaveReportPage } from "../pages/leave-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadLeave: ReportingUseCases["loadLeave"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function LeaveReportScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const changedCompany =
    parameters.has("company") && parameters.get("company") !== props.access.companyId;
  const from = changedCompany
    ? `${today.slice(0, 4)}-01-01`
    : (parameters.get("from") ?? `${today.slice(0, 4)}-01-01`);
  const until = changedCompany ? today : (parameters.get("until") ?? today);
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/leave?${new URLSearchParams({ company: props.access.companyId, from, until })}`}
      />
    );
  return (
    <LeaveReportBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      from={from}
      until={until}
      onApply={(from, until) => setParameters({ company: props.access.companyId, from, until })}
    />
  );
}

function LeaveReportBinding(
  props: Props & { from: string; until: string; onApply: (from: string, until: string) => void },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new LeaveReportController(props.loadLeave, props.access, props.from, props.until),
    [props.loadLeave, props.access, props.from, props.until],
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
    () => (state.report ? leaveReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = useLeaveReportFilters(props.from, props.until, props.locale, props.onApply);
  return (
    <LeaveReportPage
      state={state}
      view={view}
      filters={filters}
      companyName={props.companyName}
      locale={props.locale}
      onRefresh={controller.refresh}
      onOpenStatus={(status) => {
        if (state.report?.statuses.some((row) => row.status === status && row.counts.requests > 0))
          navigate(
            `/reports/leave/requests?${new URLSearchParams({ company: props.access.companyId, from: props.from, until: props.until, status })}`,
          );
      }}
      onOpenAll={() => {
        if (state.report && state.report.totals.requests > 0)
          navigate(
            `/reports/leave/requests?${new URLSearchParams({ company: props.access.companyId, from: props.from, until: props.until })}`,
          );
      }}
    />
  );
}
