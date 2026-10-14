import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollPolicyEditorController } from "../controllers/payroll-policy-editor-controller";
import { usePayrollPolicyForm } from "../controllers/use-payroll-policy-form";
import { payrollPolicyParameters, payrollPolicyQuery } from "../models/payroll-policy-route";
import { PayrollPolicyEditorPage } from "../pages/payroll-policy-editor-page";

export function PayrollPolicyEditorScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams();
  const currentMonth = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const query = payrollPolicyQuery(parameters, props.access.companyId, currentMonth);
  const canonical = payrollPolicyParameters(props.access.companyId, query.month, query.after);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/policy?${canonical}`} replace />;
  if (!parameters.has("company") || !parameters.has("month"))
    return <Navigate to={`/payroll/policy/edit?${canonical}`} replace />;
  return (
    <PayrollPolicyEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${query.month}`}
      {...props}
      month={query.month}
      backTo={`/payroll/policy?${canonical}`}
    />
  );
}
function PayrollPolicyEditorBinding(props: PayrollScreenProps & { month: string; backTo: string }) {
  const { payroll, access, month, nextIdentifier } = props;
  const controller = useMemo(
    () => new PayrollPolicyEditorController(payroll, access, month, nextIdentifier),
    [payroll, access, month, nextIdentifier],
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
  const form = usePayrollPolicyForm(controller, state, month);
  useWorkspaceRevalidation(state.failure);
  return (
    <PayrollPolicyEditorPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retrySave}
    />
  );
}
