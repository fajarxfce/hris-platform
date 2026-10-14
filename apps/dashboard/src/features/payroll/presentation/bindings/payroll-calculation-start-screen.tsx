import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollCalculationStartController } from "../controllers/payroll-calculation-start-controller";
import { usePayrollCalculationForm } from "../controllers/use-payroll-calculation-form";
import { payrollCalculationsPath, payrollRunPath } from "../models/payroll-calculation-route";
import { payrollCalculationReviewView } from "../models/payroll-calculation-view";
import { PayrollCalculationStartPage } from "../pages/payroll-calculation-start-page";
export function PayrollCalculationStartScreen(props: PayrollScreenProps) {
  const { periodId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return (
      <Navigate
        to={`/payroll/periods/${encodeURIComponent(periodId)}/calculate?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <PayrollCalculationStartBinding
      key={`${props.accountId}:${props.access.companyId}:${periodId}`}
      {...props}
      id={periodId}
    />
  );
}
function PayrollCalculationStartBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, nextIdentifier, locale } = props,
    controller = useMemo(
      () => new PayrollCalculationStartController(payroll, access, id, nextIdentifier),
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
  const form = usePayrollCalculationForm(controller, state),
    properties = useMemo(
      () => (state.review ? payrollCalculationReviewView(state.review, locale) : []),
      [state.review, locale],
    );
  return (
    <PayrollCalculationStartPage
      state={state}
      form={form}
      properties={properties}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollCalculationsPath(access.companyId, id)}
      successTo={state.receipt ? payrollRunPath(access.companyId, state.receipt.id) : null}
      onRetry={controller.retry}
    />
  );
}
