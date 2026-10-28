import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { PayrollCostReportController } from "../controllers/payroll-cost-report-controller";
import { usePayrollCostReportFilters } from "../controllers/use-payroll-cost-report-filters";
import { payrollCostReportView } from "../models/payroll-cost-report-view";
import { PayrollCostReportPage } from "../pages/payroll-cost-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadPayrollCosts: ReportingUseCases["loadPayrollCosts"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function PayrollCostReportScreen(props: Props) {
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
        to={`/reports/payroll-costs?${new URLSearchParams({ company: props.access.companyId, from, until })}`}
      />
    );
  return (
    <PayrollCostReportBinding
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

function PayrollCostReportBinding(
  props: Props & {
    from: string;
    until: string;
    onApply: (from: string, until: string) => void;
  },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () =>
      new PayrollCostReportController(
        props.loadPayrollCosts,
        props.access,
        props.from,
        props.until,
      ),
    [props.loadPayrollCosts, props.access, props.from, props.until],
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
    () => (state.report ? payrollCostReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = usePayrollCostReportFilters(props.from, props.until, props.locale, props.onApply);
  return (
    <PayrollCostReportPage
      state={state}
      view={view}
      filters={filters}
      companyName={props.companyName}
      locale={props.locale}
      onRefresh={controller.refresh}
      onOpenMonth={(month) => {
        const row = state.report?.months.find((value) => value.month === month);
        if (row && row.counts.assessments > 0)
          navigate(
            `/payroll/payslips?${new URLSearchParams({
              company: props.access.companyId,
              from: row.month,
              until: row.month,
              scope: "AUTHORIZED",
            })}`,
          );
      }}
    />
  );
}
