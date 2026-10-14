import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPeriodCancellationController } from "../controllers/payroll-period-cancellation-controller";
import { usePayrollPeriodCancellationForm } from "../controllers/use-payroll-period-cancellation-form";
import { payrollPeriodProperties } from "../models/payroll-period-view";
import { PayrollPeriodCancellationPage } from "../pages/payroll-period-cancellation-page";
export function PayrollPeriodCancellationScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams(),
    { periodId = "" } = useParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/payroll/periods/${encodeURIComponent(periodId)}/cancel?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <PayrollPeriodCancellationBinding
      key={`${props.accountId}:${props.access.companyId}:${periodId}`}
      {...props}
      id={periodId}
    />
  );
}
function PayrollPeriodCancellationBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, nextIdentifier } = props;
  const controller = useMemo(
    () => new PayrollPeriodCancellationController(payroll, access, id, nextIdentifier),
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
  const form = usePayrollPeriodCancellationForm(controller, state),
    properties = useMemo(
      () => (state.review ? payrollPeriodProperties(state.review.period, props.locale) : []),
      [state.review, props.locale],
    );
  return (
    <PayrollPeriodCancellationPage
      state={state}
      form={form}
      properties={properties}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/payroll/periods/${encodeURIComponent(id)}?company=${access.companyId}`}
      onRetry={controller.retrySave}
    />
  );
}
