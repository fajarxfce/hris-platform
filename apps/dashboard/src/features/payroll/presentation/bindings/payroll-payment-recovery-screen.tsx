import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPaymentRecoveryController } from "../controllers/payroll-payment-recovery-controller";
import { usePayrollPaymentRecoveryForm } from "../controllers/use-payroll-payment-recovery-form";
import { payrollPaymentRecoveryRoute } from "../models/payroll-payment-recovery-route";
import { payrollPaymentRecoveryView } from "../models/payroll-payment-recovery-view";
import { PayrollPaymentRecoveryPage } from "../pages/payroll-payment-recovery-page";

export function PayrollPaymentRecoveryScreen(props: PayrollScreenProps & { record?: boolean }) {
  const { payslipId = "" } = useParams(),
    [parameters] = useSearchParams(),
    route = payrollPaymentRecoveryRoute(payslipId, props.access, parameters);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/payments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return <Navigate to={props.record ? route.record : route.review} replace />;
  return (
    <PayrollPaymentRecoveryBinding
      key={`${props.accountId}:${props.access.companyId}:${payslipId}:${props.record === true}`}
      {...props}
      id={payslipId.toLowerCase()}
    />
  );
}
function PayrollPaymentRecoveryBinding(
  props: PayrollScreenProps & { id: string; record?: boolean },
) {
  const { payroll, access, id, nextIdentifier, locale, timezone } = props,
    [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate();
  const controller = useMemo(
    () => new PayrollPaymentRecoveryController(payroll, access, id, nextIdentifier),
    [payroll, access, id, nextIdentifier],
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
  const selected = parameters.get("entry"),
    route = payrollPaymentRecoveryRoute(id, access, parameters),
    form = usePayrollPaymentRecoveryForm(controller, state, timezone);
  const view = useMemo(
    () =>
      state.review ? payrollPaymentRecoveryView(state.review, selected, locale, timezone) : null,
    [state.review, selected, locale, timezone],
  );
  return (
    <PayrollPaymentRecoveryPage
      state={state}
      view={view}
      form={form}
      route={route}
      record={props.record === true}
      locale={locale}
      timezone={timezone}
      companyName={props.companyName}
      onRecord={() => {
        if (state.stage === "ready" && state.review?.canConfirm) navigate(route.record);
      }}
      onRetry={controller.retry}
      onSelected={(entry) => {
        const next = new URLSearchParams(parameters);
        if (entry === null) next.delete("entry");
        else if (state.review?.recoveries.some((item) => item.id === entry))
          next.set("entry", entry);
        else return;
        setParameters(next, { replace: true });
      }}
    />
  );
}
