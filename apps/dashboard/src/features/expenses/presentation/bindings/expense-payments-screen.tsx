import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpensePaymentSearchInput } from "../../domain/entities/expense-payment-search";
import { canReadExpensePayments } from "../../domain/policies/expense-payment-read-policy";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { useExpensePaymentFilters } from "../controllers/use-expense-payment-filters";
import { useExpensePayments } from "../controllers/use-expense-payments";
import {
  expensePaymentParameters,
  expensePaymentPath,
  expensePaymentSearchFromRoute,
} from "../models/expense-payment-route";
import { expensePaymentRows } from "../models/expense-payment-view";
import { ExpensePaymentsPage } from "../pages/expense-payments-page";
export function ExpensePaymentsScreen(props: ExpenseScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => expensePaymentSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  if (parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/expenses/payments?${expensePaymentParameters(props.access.companyId, search)}`}
        replace
      />
    );
  return (
    <ExpensePaymentsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(expensePaymentParameters(props.access.companyId, next))}
    />
  );
}
function ExpensePaymentsBinding(
  props: ExpenseScreenProps & {
    search: ExpensePaymentSearchInput;
    onSearch: (search: ExpensePaymentSearchInput) => void;
  },
) {
  const navigate = useNavigate();
  const state = useExpensePayments(
    props.accountId,
    props.access,
    props.search,
    props.expense.loadPayments,
  );
  const filters = useExpensePaymentFilters(props.search, props.locale, props.onSearch);
  const rows = useMemo(
    () => (state.page ? expensePaymentRows(state.page, props.locale, props.timezone) : []),
    [state.page, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <ExpensePaymentsPage
      state={state}
      filters={filters}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      canPrepare={canReadExpensePayments(props.access.permissions)}
      onPrepare={() =>
        navigate(
          `/expenses/payments/new?${expensePaymentParameters(props.access.companyId, props.search)}`,
        )
      }
      firstPage={props.search.after === null}
      onFirst={() => props.onSearch({ ...props.search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor)
          props.onSearch({ ...props.search, after: state.page.nextCursor });
      }}
      onOpen={(id) => {
        if (state.page?.items.some((batch) => batch.id === id))
          navigate(
            `${expensePaymentPath(id)}?${expensePaymentParameters(props.access.companyId, props.search)}`,
          );
      }}
    />
  );
}
