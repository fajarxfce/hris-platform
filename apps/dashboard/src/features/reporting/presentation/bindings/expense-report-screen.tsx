import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ExpenseReportController } from "../controllers/expense-report-controller";
import { useExpenseReportFilters } from "../controllers/use-expense-report-filters";
import { expenseReportView } from "../models/expense-report-view";
import { saveAppliedReportFilter } from "../models/saved-report-filter-route";
import { ExpenseReportPage } from "../pages/expense-report-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadExpenses: ReportingUseCases["loadExpenses"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function ExpenseReportScreen(props: Props) {
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
        to={`/reports/expenses?${new URLSearchParams({ company: props.access.companyId, from, until })}`}
      />
    );
  return (
    <ExpenseReportBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      from={from}
      until={until}
      onApply={(from, until) => setParameters({ company: props.access.companyId, from, until })}
    />
  );
}

function ExpenseReportBinding(
  props: Props & { from: string; until: string; onApply: (from: string, until: string) => void },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new ExpenseReportController(props.loadExpenses, props.access, props.from, props.until),
    [props.loadExpenses, props.access, props.from, props.until],
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
    () => (state.report ? expenseReportView(state.report, props.locale) : null),
    [state.report, props.locale],
  );
  const filters = useExpenseReportFilters(props.from, props.until, props.locale, props.onApply);
  return (
    <ExpenseReportPage
      savedFilterTo={
        state.report
          ? saveAppliedReportFilter(props.access.companyId, "EXPENSES", {
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
      onOpenStatus={(status) => {
        if (state.report?.statuses.some((row) => row.status === status && row.counts.claims > 0))
          navigate(
            `/reports/expenses/claims?${new URLSearchParams({ company: props.access.companyId, from: props.from, until: props.until, status })}`,
          );
      }}
      onOpenAll={() => {
        if (state.report && state.report.totals.claims > 0)
          navigate(
            `/reports/expenses/claims?${new URLSearchParams({ company: props.access.companyId, from: props.from, until: props.until })}`,
          );
      }}
    />
  );
}
