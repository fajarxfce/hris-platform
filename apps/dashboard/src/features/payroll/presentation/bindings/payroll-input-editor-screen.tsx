import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollInputEditorController } from "../controllers/payroll-input-editor-controller";
import { usePayrollInputForm } from "../controllers/use-payroll-input-form";
import { payrollInputReviewParameters } from "../models/payroll-input-route";
import { payrollInputReviewView } from "../models/payroll-input-view";
import { PayrollInputEditorPage } from "../pages/payroll-input-editor-page";
import { PayrollInputTermFields } from "./payroll-input-term-fields";

export function PayrollInputEditorScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const month = parameters.get("month") ?? today;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/inputs?company=${props.access.companyId}`} replace />;
  const query = payrollInputReviewParameters(props.access.companyId, month);
  if (!parameters.has("company") || !parameters.has("month"))
    return (
      <Navigate to={`/payroll/inputs/${encodeURIComponent(employeeId)}/edit?${query}`} replace />
    );
  return (
    <PayrollInputEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${month}`}
      {...props}
      employee={employeeId}
      month={month}
      backTo={`/payroll/inputs/${encodeURIComponent(employeeId)}?${query}`}
    />
  );
}
function PayrollInputEditorBinding(
  props: PayrollScreenProps & { employee: string; month: string; backTo: string },
) {
  const { payroll, access, employee, month, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () => new PayrollInputEditorController(payroll, access, employee, month, nextIdentifier),
    [payroll, access, employee, month, nextIdentifier],
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
  const form = usePayrollInputForm(controller, state);
  const source = useMemo(
    () => (state.context ? payrollInputReviewView(state.context, locale, timezone).source : null),
    [state.context, locale, timezone],
  );
  return (
    <PayrollInputEditorPage
      state={state}
      form={form}
      source={source}
      locale={locale}
      companyName={props.companyName}
      backTo={props.backTo}
      onRetry={controller.retrySave}
    >
      <PayrollInputTermFields form={form} failure={state.failure} locale={locale} />
    </PayrollInputEditorPage>
  );
}
