import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPaymentTransitionController } from "../controllers/payroll-payment-transition-controller";
import { usePayrollPaymentTransitionForm } from "../controllers/use-payroll-payment-transition-form";
import { payrollPaymentPath } from "../models/payroll-payment-route";
import {
  type PayrollPaymentTransitionIntent,
  payrollPaymentTransitionForIntent,
  payrollPaymentTransitionIntents,
} from "../models/payroll-payment-transition-intent";
import { payrollPaymentTransitionView } from "../models/payroll-payment-transition-view";
import { PayrollPaymentTransitionPage } from "../pages/payroll-payment-transition-page";

type Props = PayrollScreenProps & { nextIdentifier: () => string };
export function PayrollPaymentTransitionScreen(props: Props) {
  const { batchId = "", intent: inputIntent = "" } = useParams();
  const [parameters] = useSearchParams(),
    intent = payrollPaymentTransitionIntents.find((value) => value === inputIntent);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payments?company=${props.access.companyId}`} replace />;
  const query = new URLSearchParams(parameters);
  query.set("company", props.access.companyId);
  query.delete("view");
  query.delete("item");
  query.delete("change");
  query.delete("historyAfter");
  const backTo = `${payrollPaymentPath(batchId)}?${query}`;
  if (!intent) return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${payrollPaymentPath(batchId)}/${intent}?${query}`} replace />;
  return (
    <PayrollPaymentTransitionBinding
      key={`${props.accountId}:${props.access.companyId}:${batchId}:${intent}`}
      {...props}
      id={batchId}
      intent={intent}
      backTo={backTo}
    />
  );
}
function PayrollPaymentTransitionBinding(
  props: Props & { id: string; intent: PayrollPaymentTransitionIntent; backTo: string },
) {
  const controller = useMemo(
    () =>
      new PayrollPaymentTransitionController(
        props.payroll,
        props.access,
        props.id,
        payrollPaymentTransitionForIntent[props.intent],
        props.nextIdentifier,
      ),
    [props.payroll, props.access, props.id, props.intent, props.nextIdentifier],
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
  const form = usePayrollPaymentTransitionForm(controller, state);
  const view = useMemo(
    () =>
      state.review
        ? payrollPaymentTransitionView(
            state.review,
            state.selectedItemId,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, state.selectedItemId, props.locale, props.timezone],
  );
  return (
    <PayrollPaymentTransitionPage
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
