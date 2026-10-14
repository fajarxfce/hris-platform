import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPaymentReconciliationController } from "../controllers/payroll-payment-reconciliation-controller";
import { usePayrollPaymentOutcomeForm } from "../controllers/use-payroll-payment-outcome-form";
import { usePayrollPaymentReconciliationForm } from "../controllers/use-payroll-payment-reconciliation-form";
import type { PayrollPaymentReconciliationState } from "../models/payroll-payment-reconciliation-state";
import { payrollPaymentReconciliationView } from "../models/payroll-payment-reconciliation-view";
import { payrollPaymentPath } from "../models/payroll-payment-route";
import { PayrollPaymentOutcomePage } from "../pages/payroll-payment-outcome-page";
import { PayrollPaymentReconciliationPage } from "../pages/payroll-payment-reconciliation-page";

type Props = PayrollScreenProps & { nextIdentifier: () => string };
export function PayrollPaymentReconciliationScreen(props: Props) {
  const { batchId = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payments?company=${props.access.companyId}`} replace />;
  const query = new URLSearchParams(parameters);
  query.set("company", props.access.companyId);
  query.delete("view");
  query.delete("item");
  query.delete("change");
  query.delete("historyAfter");
  const backTo = `${payrollPaymentPath(batchId)}?${query}`;
  if (!parameters.has("company"))
    return <Navigate to={`${payrollPaymentPath(batchId)}/reconcile?${query}`} replace />;
  return (
    <PayrollPaymentReconciliationBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}`}
      {...props}
      id={batchId}
      backTo={backTo}
    />
  );
}
function PayrollPaymentReconciliationBinding(props: Props & { id: string; backTo: string }) {
  const controller = useMemo(
    () =>
      new PayrollPaymentReconciliationController(
        props.payroll,
        props.access,
        props.id,
        props.nextIdentifier,
      ),
    [props.payroll, props.access, props.id, props.nextIdentifier],
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
  const form = usePayrollPaymentReconciliationForm(controller, state);
  const view = useMemo(
    () =>
      state.review
        ? payrollPaymentReconciliationView(
            state.review,
            state.results,
            state.selectedItemId,
            props.locale,
          )
        : null,
    [state.review, state.results, state.selectedItemId, props.locale],
  );
  return (
    <PayrollPaymentReconciliationPage
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
          <PayrollPaymentOutcomeBinding
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
function PayrollPaymentOutcomeBinding(
  props: Pick<PayrollScreenProps, "locale" | "timezone"> & {
    controller: PayrollPaymentReconciliationController;
    item: NonNullable<ReturnType<typeof payrollPaymentReconciliationView>["selected"]>;
    failure: PayrollPaymentReconciliationState["entryFailure"];
  },
) {
  const form = usePayrollPaymentOutcomeForm(
    props.controller,
    props.item.id,
    props.item.original,
    props.timezone,
  );
  return (
    <PayrollPaymentOutcomePage
      form={form}
      item={props.item}
      failure={props.failure}
      timezone={props.timezone}
      locale={props.locale}
    />
  );
}
