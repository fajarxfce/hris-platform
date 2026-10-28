import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseReportQuery } from "../../domain/entities/expense-report-claim";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { ExpenseReportClaimsController } from "../controllers/expense-report-claims-controller";
import { useExpenseReportFilters } from "../controllers/use-expense-report-filters";
import { expenseReportParameters, expenseReportQuery } from "../models/expense-report-route";
import { expenseReportClaimsView } from "../models/expense-report-view";
import { ExpenseReportClaimsPage } from "../pages/expense-report-claims-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  loadExpenseClaims: ReportingUseCases["loadExpenseClaims"];
  companyName: string;
  timezone: string;
  locale: Locale;
};

export function ExpenseReportClaimsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = useMemo(
    () => expenseReportQuery(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        replace
        to={`/reports/expenses/claims?${expenseReportParameters(props.access.companyId, query)}`}
      />
    );
  return (
    <ExpenseReportClaimsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQuery={(next) => setParameters(expenseReportParameters(props.access.companyId, next))}
    />
  );
}

function ExpenseReportClaimsBinding(
  props: Props & { query: ExpenseReportQuery; onQuery: (query: ExpenseReportQuery) => void },
) {
  const navigate = useNavigate();
  const controller = useMemo(
    () => new ExpenseReportClaimsController(props.loadExpenseClaims, props.access, props.query),
    [props.loadExpenseClaims, props.access, props.query],
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
  const filters = useExpenseReportFilters(
    props.query.from,
    props.query.until,
    props.locale,
    (from, until, status) => props.onQuery({ from, until, status, after: null }),
    props.query.status,
  );
  const rows = useMemo(
    () => (state.page ? expenseReportClaimsView(state.page, props.locale) : []),
    [state.page, props.locale],
  );
  return (
    <ExpenseReportClaimsPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={props.companyName}
      locale={props.locale}
      firstPage={props.query.after === null}
      backTo={`/reports/expenses?${expenseReportParameters(props.access.companyId, { ...props.query, status: null, after: null })}`}
      onRefresh={controller.refresh}
      onFirst={() => props.onQuery({ ...props.query, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) props.onQuery({ ...props.query, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        const row = state.page?.items.find((row) => row.id === id);
        if (row) {
          const query = new URLSearchParams({
            company: props.access.companyId,
            reportFrom: props.query.from,
            reportUntil: props.query.until,
          });
          if (props.query.status !== null) query.set("reportStatus", props.query.status);
          if (props.query.after !== null) query.set("reportAfter", props.query.after);
          navigate(`/expenses/submissions/${encodeURIComponent(row.submissionId)}?${query}`);
        }
      }}
    />
  );
}
