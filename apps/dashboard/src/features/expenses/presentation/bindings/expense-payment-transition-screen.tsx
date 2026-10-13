import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpensePaymentTransitionController } from "../controllers/expense-payment-transition-controller";
import { useExpensePaymentTransitionForm } from "../controllers/use-expense-payment-transition-form";
import { expensePaymentPath } from "../models/expense-payment-route";
import {
  type ExpensePaymentTransitionIntent,
  expensePaymentTransitionForIntent,
  expensePaymentTransitionIntents,
} from "../models/expense-payment-transition-intent";
import { expensePaymentTransitionView } from "../models/expense-payment-transition-view";
import { ExpensePaymentTransitionPage } from "../pages/expense-payment-transition-page";

type Props = ExpenseScreenProps & { nextIdentifier: () => string };
export function ExpensePaymentTransitionScreen(props: Props) {
  const { batchId = "", intent: inputIntent = "" } = useParams();
  const [parameters] = useSearchParams(),
    intent = expensePaymentTransitionIntents.find((value) => value === inputIntent);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/payments?company=${props.access.companyId}`} replace />;
  const query = new URLSearchParams(parameters);
  query.set("company", props.access.companyId);
  query.delete("view");
  query.delete("item");
  query.delete("change");
  query.delete("historyAfter");
  const backTo = `${expensePaymentPath(batchId)}?${query}`;
  if (!intent) return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${expensePaymentPath(batchId)}/${intent}?${query}`} replace />;
  return (
    <ExpensePaymentTransitionBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}:${intent}`}
      {...props}
      id={batchId}
      intent={intent}
      backTo={backTo}
    />
  );
}
function ExpensePaymentTransitionBinding(
  props: Props & { id: string; intent: ExpensePaymentTransitionIntent; backTo: string },
) {
  const controller = useMemo(
    () =>
      new ExpensePaymentTransitionController(
        props.expense,
        props.access,
        props.id,
        expensePaymentTransitionForIntent[props.intent],
        props.nextIdentifier,
      ),
    [props.expense, props.access, props.id, props.intent, props.nextIdentifier],
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
  const form = useExpensePaymentTransitionForm(controller, state);
  const view = useMemo(
    () =>
      state.review
        ? expensePaymentTransitionView(
            state.review,
            state.selectedItemId,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, state.selectedItemId, props.locale, props.timezone],
  );
  return (
    <ExpensePaymentTransitionPage
      state={state}
      form={form}
      view={view}
      intent={props.intent}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
      onItem={controller.selectItem}
    />
  );
}
