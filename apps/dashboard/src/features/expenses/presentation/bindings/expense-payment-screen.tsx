import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { useExpensePayment } from "../controllers/use-expense-payment";
import {
  expensePaymentParameters,
  expensePaymentPath,
  expensePaymentSearchFromRoute,
  expensePaymentTab,
  expensePaymentTabs,
} from "../models/expense-payment-route";
import { expensePaymentTransitionActions } from "../models/expense-payment-transition-view";
import { expensePaymentView } from "../models/expense-payment-view";
import { ExpensePaymentPage } from "../pages/expense-payment-page";
import { ExpensePaymentWorksheet } from "./expense-payment-worksheet";
export function ExpensePaymentScreen(props: ExpenseScreenProps) {
  const { batchId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const directory = expensePaymentParameters(
    props.access.companyId,
    expensePaymentSearchFromRoute(parameters, props.access.companyId, today),
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/payments?${directory}`} replace />;
  if (!parameters.has("company")) {
    if (parameters.has("historyAfter"))
      directory.set("historyAfter", parameters.get("historyAfter") ?? "");
    directory.set("view", expensePaymentTab(parameters.get("view")));
    return <Navigate to={`${expensePaymentPath(batchId)}?${directory}`} replace />;
  }
  return (
    <ExpensePaymentBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}`}
      {...props}
      id={batchId}
      directory={directory.toString()}
    />
  );
}
function ExpensePaymentBinding(props: ExpenseScreenProps & { id: string; directory: string }) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after = parameters.get("historyAfter"),
    tab = expensePaymentTab(parameters.get("view"));
  const selectedItem = tab === "instructions" || tab === "results" ? parameters.get("item") : null;
  const selectedChange = tab === "history" ? parameters.get("change") : null;
  const state = useExpensePayment(
    props.accountId,
    props.access,
    props.id,
    after,
    props.expense.loadPayment,
  );
  const view = useMemo(
    () =>
      state.review
        ? expensePaymentView(
            state.review,
            selectedItem,
            selectedChange,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, selectedItem, selectedChange, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  const transitions = state.review
    ? expensePaymentTransitionActions(props.access, state.review, props.locale)
    : [];
  return (
    <ExpensePaymentPage
      state={state}
      view={view}
      transitions={transitions}
      onTransition={(intent) => {
        if (transitions.some((item) => item.intent === intent))
          navigate(`${expensePaymentPath(props.id)}/${intent}?${parameters}`);
      }}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/expenses/payments?${props.directory}`}
      tab={tab}
      firstHistory={after === null}
      worksheet={
        state.review?.availableActions.includes("EXPORT") ? (
          <ExpensePaymentWorksheet
            download={props.expense.downloadPayment}
            access={props.access}
            review={state.review}
            locale={props.locale}
            onUnavailable={state.withdrawReview}
          />
        ) : null
      }
      onTab={(value) => {
        if (!expensePaymentTabs.some((item) => item === value)) return;
        const next = new URLSearchParams(parameters);
        next.set("view", value);
        next.delete("item");
        next.delete("change");
        setParameters(next);
      }}
      onItem={(id) => {
        const next = new URLSearchParams(parameters);
        if (id === null) next.delete("item");
        else if (state.review?.batch.items.some((item) => item.id === id)) next.set("item", id);
        setParameters(next, { replace: true });
      }}
      onChange={(version) => {
        const next = new URLSearchParams(parameters);
        if (version === null) next.delete("change");
        else if (state.review?.history.items.some((item) => String(item.version) === version))
          next.set("change", version);
        setParameters(next, { replace: true });
      }}
      onFirstHistory={() => {
        const next = new URLSearchParams(parameters);
        next.delete("historyAfter");
        next.delete("change");
        setParameters(next);
      }}
      onNextHistory={() => {
        if (!state.review?.history.nextCursor) return;
        const next = new URLSearchParams(parameters);
        next.set("historyAfter", state.review.history.nextCursor);
        next.delete("change");
        setParameters(next);
      }}
    />
  );
}
