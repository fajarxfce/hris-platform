import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollPayableSearch } from "../../domain/entities/payroll-payable-search";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPaymentPreparationController } from "../controllers/payroll-payment-preparation-controller";
import { usePayrollPayablePicker } from "../controllers/use-payroll-payable-picker";
import { usePayrollPaymentDestinationForm } from "../controllers/use-payroll-payment-destination-form";
import { usePayrollPaymentPreparationForm } from "../controllers/use-payroll-payment-preparation-form";
import {
  payrollPayableRows,
  payrollPaymentPreparationView,
} from "../models/payroll-payment-preparation-view";
import {
  payrollPaymentParameters,
  payrollPaymentPath,
  payrollPaymentSearchFromRoute,
} from "../models/payroll-payment-route";
import { PayrollPayablePickerPage } from "../pages/payroll-payable-picker-page";
import { PayrollPaymentDestinationPage } from "../pages/payroll-payment-destination-page";
import { PayrollPaymentPreparationPage } from "../pages/payroll-payment-preparation-page";

type Props = PayrollScreenProps & { nextIdentifier: () => string };
export function PayrollPaymentPreparationScreen(props: Props) {
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => payrollPaymentSearchFromRoute(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  const query = payrollPaymentParameters(props.access.companyId, search);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payments?${query}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`/payroll/payments/new?${query}`} replace />;
  return (
    <PayrollPaymentPreparationBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      initial={{ from: search.from, until: search.until, after: null }}
      query={query.toString()}
    />
  );
}

function PayrollPaymentPreparationBinding(
  props: Props & { initial: PayrollPayableSearch; query: string },
) {
  const controller = useMemo(
    () =>
      new PayrollPaymentPreparationController(
        props.payroll.preparePayment,
        props.access,
        props.nextIdentifier,
      ),
    [props.payroll.preparePayment, props.access, props.nextIdentifier],
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
  const form = usePayrollPaymentPreparationForm(controller, state);
  const view = useMemo(
    () => payrollPaymentPreparationView(state, props.locale),
    [state, props.locale],
  );
  const selectedIds = useMemo(
    () => new Set(state.items.map((item) => item.payable.assessmentId)),
    [state.items],
  );
  return (
    <PayrollPaymentPreparationPage
      state={state}
      form={form}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/payroll/payments?${props.query}`}
      savedTo={state.receipt ? `${payrollPaymentPath(state.receipt.id)}?${props.query}` : null}
      onAdd={controller.openPayables}
      onDestination={controller.selectDestination}
      onEdit={controller.edit}
      onConfirm={controller.confirm}
      onRetry={controller.retry}
      panel={
        state.stage === "editing" && state.panel.kind === "payables" ? (
          <PayrollPayablePickerBinding
            {...props}
            controller={controller}
            selectedIds={selectedIds}
            failure={state.entryFailure}
          />
        ) : state.stage === "editing" && view.selected ? (
          <PayrollPaymentDestinationBinding
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

function PayrollPayablePickerBinding(
  props: PayrollScreenProps & {
    controller: PayrollPaymentPreparationController;
    initial: PayrollPayableSearch;
    selectedIds: ReadonlySet<string>;
    failure: Failure | null;
  },
) {
  const state = usePayrollPayablePicker(
    props.accountId,
    props.access,
    props.initial,
    props.payroll.loadPayables,
  );
  const rows = useMemo(
    () =>
      state.page
        ? payrollPayableRows(
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
    <PayrollPayablePickerPage
      state={state}
      rows={rows}
      selectionFailure={props.failure}
      locale={props.locale}
      onClose={props.controller.closePanel}
      onAdd={(id) => {
        const payable = state.page?.items.find((item) => item.assessmentId === id);
        if (payable) props.controller.addPayable(payable);
      }}
    />
  );
}

function PayrollPaymentDestinationBinding(props: {
  controller: PayrollPaymentPreparationController;
  item: NonNullable<ReturnType<typeof payrollPaymentPreparationView>["selected"]>;
  failure: Failure | null;
  locale: PayrollScreenProps["locale"];
}) {
  const form = usePayrollPaymentDestinationForm(
    props.controller,
    props.item.id,
    props.item.original,
  );
  return (
    <PayrollPaymentDestinationPage
      form={form}
      item={props.item}
      failure={props.failure}
      locale={props.locale}
    />
  );
}
