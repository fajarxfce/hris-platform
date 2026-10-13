import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpenseReconciliationController } from "../controllers/expense-reconciliation-controller";
import { useExpenseOutcomeForm } from "../controllers/use-expense-outcome-form";
import { useExpenseReconciliationForm } from "../controllers/use-expense-reconciliation-form";
import { expensePaymentPath } from "../models/expense-payment-route";
import type { ExpenseReconciliationState } from "../models/expense-reconciliation-state";
import { expenseReconciliationView } from "../models/expense-reconciliation-view";
import { ExpenseOutcomePage } from "../pages/expense-outcome-page";
import { ExpenseReconciliationPage } from "../pages/expense-reconciliation-page";

type Props = ExpenseScreenProps & { nextIdentifier: () => string };
export function ExpenseReconciliationScreen(props: Props) {
  const { batchId = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/payments?company=${props.access.companyId}`} replace />;
  const query = new URLSearchParams(parameters);
  query.set("company", props.access.companyId);
  query.delete("view");
  query.delete("item");
  query.delete("change");
  query.delete("historyAfter");
  const backTo = `${expensePaymentPath(batchId)}?${query}`;
  if (!parameters.has("company"))
    return <Navigate to={`${expensePaymentPath(batchId)}/reconcile?${query}`} replace />;
  return (
    <ExpenseReconciliationBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}`}
      {...props}
      id={batchId}
      backTo={backTo}
    />
  );
}
function ExpenseReconciliationBinding(props: Props & { id: string; backTo: string }) {
  const controller = useMemo(
    () =>
      new ExpenseReconciliationController(
        props.expense,
        props.access,
        props.id,
        props.nextIdentifier,
      ),
    [props.expense, props.access, props.id, props.nextIdentifier],
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
  const form = useExpenseReconciliationForm(controller, state);
  const view = useMemo(
    () =>
      state.review
        ? expenseReconciliationView(state.review, state.results, state.selectedItemId, props.locale)
        : null,
    [state.review, state.results, state.selectedItemId, props.locale],
  );
  return (
    <ExpenseReconciliationPage
      state={state}
      form={form}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
      onItem={controller.selectItem}
      editor={
        state.stage === "reviewing" && view?.selected ? (
          <ExpenseOutcomeBinding
            key={view.selected.id}
            controller={controller}
            item={view.selected}
            failure={state.entryFailure}
            timezone={props.timezone}
            locale={props.locale}
          />
        ) : null
      }
    />
  );
}
function ExpenseOutcomeBinding(
  props: Pick<ExpenseScreenProps, "locale" | "timezone"> & {
    controller: ExpenseReconciliationController;
    item: NonNullable<ReturnType<typeof expenseReconciliationView>["selected"]>;
    failure: ExpenseReconciliationState["entryFailure"];
  },
) {
  const form = useExpenseOutcomeForm(
    props.controller,
    props.item.id,
    props.item.original,
    props.timezone,
  );
  return (
    <ExpenseOutcomePage
      form={form}
      item={props.item}
      failure={props.failure}
      timezone={props.timezone}
      locale={props.locale}
    />
  );
}
