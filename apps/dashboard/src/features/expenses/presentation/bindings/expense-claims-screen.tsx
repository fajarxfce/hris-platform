import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseClaimSearchInput } from "../../domain/entities/expense-claim-search";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { useExpenseClaimFilters } from "../controllers/use-expense-claim-filters";
import { useExpenseClaims } from "../controllers/use-expense-claims";
import {
  expenseClaimParameters,
  expenseClaimPath,
  expenseClaimSearchFromRoute,
} from "../models/expense-claim-route";
import { expenseClaimRows } from "../models/expense-claim-view";
import { ExpenseClaimsPage } from "../pages/expense-claims-page";
export function ExpenseClaimsScreen(props: ExpenseScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => expenseClaimSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/expenses/claims?${expenseClaimParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <ExpenseClaimsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(expenseClaimParameters(props.access.companyId, next))}
    />
  );
}
function ExpenseClaimsBinding(
  props: ExpenseScreenProps & {
    search: ExpenseClaimSearchInput;
    onSearch: (search: ExpenseClaimSearchInput) => void;
  },
) {
  const navigate = useNavigate();
  const state = useExpenseClaims(
    props.accountId,
    props.access,
    props.search,
    props.expense.loadClaims,
  );
  const filters = useExpenseClaimFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? expenseClaimRows(state.page, props.locale, props.timezone) : []),
    [state.page, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <ExpenseClaimsPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      firstPage={props.search.after === null}
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((claim) => claim.id === id))
          navigate(
            `${expenseClaimPath(id)}?${expenseClaimParameters(props.access.companyId, props.search)}`,
          );
      }}
    />
  );
}
