import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpensePayableSearch } from "../../domain/entities/expense-payable-search";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpensePreparationController } from "../controllers/expense-preparation-controller";
import { useExpenseDestinationForm } from "../controllers/use-expense-destination-form";
import { useExpensePayablePicker } from "../controllers/use-expense-payable-picker";
import { useExpensePreparationForm } from "../controllers/use-expense-preparation-form";
import {
  expensePaymentParameters,
  expensePaymentPath,
  expensePaymentSearchFromRoute,
} from "../models/expense-payment-route";
import { expensePayableRows, expensePreparationView } from "../models/expense-preparation-view";
import { ExpenseDestinationPage } from "../pages/expense-destination-page";
import { ExpensePayablePickerPage } from "../pages/expense-payable-picker-page";
import { ExpensePreparationPage } from "../pages/expense-preparation-page";

type Props = ExpenseScreenProps & { nextIdentifier: () => string };
export function ExpensePreparationScreen(props: Props) {
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => expensePaymentSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  const query = expensePaymentParameters(props.access.companyId, search);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/payments?${query}`} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`/expenses/payments/new?${query}`} replace />;
  return (
    <ExpensePreparationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      initial={{ from: search.from, until: search.until, after: null }}
      query={query.toString()}
    />
  );
}

function ExpensePreparationBinding(
  props: Props & { initial: ExpensePayableSearch; query: string },
) {
  const controller = useMemo(
    () =>
      new ExpensePreparationController(
        props.expense.preparePayment,
        props.access,
        props.nextIdentifier,
      ),
    [props.expense.preparePayment, props.access, props.nextIdentifier],
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
  const form = useExpensePreparationForm(controller, state);
  const view = useMemo(() => expensePreparationView(state, props.locale), [state, props.locale]);
  const selectedIds = useMemo(
    () => new Set(state.items.map((item) => item.payable.claimId)),
    [state.items],
  );
  return (
    <ExpensePreparationPage
      state={state}
      form={form}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/expenses/payments?${props.query}`}
      savedTo={state.receipt ? `${expensePaymentPath(state.receipt.id)}?${props.query}` : null}
      onAdd={controller.openPayables}
      onDestination={controller.selectDestination}
      onEdit={controller.edit}
      onConfirm={controller.confirm}
      onRetry={controller.retry}
      panel={
        state.stage === "editing" && state.panel.kind === "payables" ? (
          <ExpensePayablePickerBinding
            {...props}
            controller={controller}
            selectedIds={selectedIds}
            failure={state.entryFailure}
          />
        ) : state.stage === "editing" && view.selected ? (
          <ExpenseDestinationBinding
            key={view.selected.id}
            controller={controller}
            item={view.selected}
            failure={state.entryFailure}
            locale={props.locale}
          />
        ) : null
      }
    />
  );
}

function ExpensePayablePickerBinding(
  props: ExpenseScreenProps & {
    controller: ExpensePreparationController;
    initial: ExpensePayableSearch;
    selectedIds: ReadonlySet<string>;
    failure: Failure | null;
  },
) {
  const state = useExpensePayablePicker(
    props.accountId,
    props.access,
    props.initial,
    props.expense.loadPayables,
  );
  const rows = useMemo(
    () =>
      state.page
        ? expensePayableRows(
            state.page,
            props.selectedIds,
            props.access,
            props.locale,
            props.timezone,
          )
        : [],
    [state.page, props.selectedIds, props.access, props.locale, props.timezone],
  );
  useEffect(() => {
    if (state.failure) props.controller.withdrawEvidence(state.failure);
  }, [props.controller, state.failure]);
  return (
    <ExpensePayablePickerPage
      state={state}
      rows={rows}
      selectionFailure={props.failure}
      locale={props.locale}
      onClose={props.controller.closePanel}
      onAdd={(id) => {
        const payable = state.page?.items.find((item) => item.claimId === id);
        if (payable) props.controller.addPayable(payable);
      }}
    />
  );
}

function ExpenseDestinationBinding(props: {
  controller: ExpensePreparationController;
  item: NonNullable<ReturnType<typeof expensePreparationView>["selected"]>;
  failure: Failure | null;
  locale: ExpenseScreenProps["locale"];
}) {
  const form = useExpenseDestinationForm(props.controller, props.item.id, props.item.original);
  return (
    <ExpenseDestinationPage
      form={form}
      item={props.item}
      failure={props.failure}
      locale={props.locale}
    />
  );
}
