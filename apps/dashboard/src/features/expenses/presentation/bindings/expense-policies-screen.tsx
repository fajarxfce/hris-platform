import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePolicyQuery } from "../../domain/entities/expense-policy-definition";
import { canManageExpensePolicies } from "../../domain/policies/expense-read-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePoliciesController } from "../controllers/expense-policies-controller";
import { useExpensePolicyFilters } from "../controllers/use-expense-policy-filters";
import { expensePolicyParameters, expensePolicyQuery } from "../models/expense-policy-route";
import { expensePoliciesView } from "../models/expense-policy-view";
import { ExpensePoliciesPage } from "../pages/expense-policies-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  expense: ExpenseUseCases;
  companyName: string;
  locale: Locale;
};
export function ExpensePoliciesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const query = useMemo(
    () => expensePolicyQuery(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  const canonical = expensePolicyParameters(props.access.companyId, query).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/policies?${canonical}`} replace />;
  return (
    <ExpensePoliciesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      query={query}
      onQuery={(value) => setParameters(expensePolicyParameters(props.access.companyId, value))}
      onOpen={(id) => navigate(`/expenses/policies/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function ExpensePoliciesBinding({
  access,
  expense,
  companyName,
  locale,
  query,
  onQuery,
  onOpen,
}: Props & {
  query: ExpensePolicyQuery;
  onQuery: (query: ExpensePolicyQuery) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new ExpensePoliciesController(expense.loadPolicies, access, query),
    [expense.loadPolicies, access, query],
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
  const filters = useExpensePolicyFilters(query, onQuery);
  const rows = useMemo(
    () => (state.page ? expensePoliciesView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <ExpensePoliciesPage
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      filters={filters}
      createTo={
        canManageExpensePolicies(access.permissions)
          ? `/expenses/policies/new?${expensePolicyParameters(access.companyId, query)}`
          : null
      }
      firstPage={query.after === null}
      onRefresh={controller.refresh}
      onFirst={() => onQuery({ ...query, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onQuery({ ...query, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
