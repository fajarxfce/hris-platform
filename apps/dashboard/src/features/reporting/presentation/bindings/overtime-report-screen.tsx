import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { OvertimeReportController } from "../controllers/overtime-report-controller";
import { useOvertimeReportFilters } from "../controllers/use-overtime-report-filters";
import { overtimeReportView } from "../models/overtime-report-view";
import { saveAppliedReportFilter } from "../models/saved-report-filter-route";
import { OvertimeReportPage } from "../pages/overtime-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadOvertime: ReportingUseCases["loadOvertime"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function OvertimeReportScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const changedCompany =
    parameters.has("company") && parameters.get("company") !== props.access.companyId;
  const from = changedCompany
    ? `${today.slice(0, 4)}-01-01`
    : (parameters.get("from") ?? `${today.slice(0, 4)}-01-01`);
  const status = changedCompany ? null : parameters.get("status");
  const until = changedCompany ? today : (parameters.get("until") ?? today);
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/overtime?${new URLSearchParams({ company: props.access.companyId, from, until, ...(status === null ? {} : { status }) })}`}
      />
    );
  return (
    <OvertimeReportBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      from={from}
      until={until}
      status={status}
      onApply={(from, until, status) =>
        setParameters({
          company: props.access.companyId,
          from,
          until,
          ...(status === null ? {} : { status }),
        })
      }
    />
  );
}

function OvertimeReportBinding(
  props: Props & {
    from: string;
    until: string;
    status: string | null;
    onApply: (from: string, until: string, status: string | null) => void;
  },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new OvertimeReportController(
        props.loadOvertime,
        props.access,
        props.from,
        props.until,
        props.status,
      ),
    [props.loadOvertime, props.access, props.from, props.until, props.status],
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
    () => (state.report ? overtimeReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = useOvertimeReportFilters(
    props.from,
    props.until,
    props.locale,
    props.onApply,
    props.status,
  );
  return (
    <OvertimeReportPage
      savedFilterTo={
        state.report
          ? saveAppliedReportFilter(props.access.companyId, "OVERTIME", {
              asOf: null,
              from: props.from,
              until: props.until,
              status: props.status,
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
        if (row && row.counts.requests > 0)
          navigate(
            `/workforce/overtime?${new URLSearchParams({
              company: props.access.companyId,
              from: row.from,
              until: row.until,
              ...(state.report?.status ? { status: state.report.status } : {}),
            })}`,
          );
      }}
    />
  );
}
