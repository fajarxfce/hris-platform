import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { CompensationEditorController } from "../controllers/compensation-editor-controller";
import { useCompensationForm } from "../controllers/use-compensation-form";
import {
  compensationEmployeeParameters,
  compensationReviewParameters,
} from "../models/compensation-route";
import { CompensationDeductionFields } from "../pages/compensation-deduction-fields";
import { CompensationEarningsFields } from "../pages/compensation-earnings-fields";
import { CompensationEditorPage } from "../pages/compensation-editor-page";
import { CompensationInsuranceFields } from "../pages/compensation-insurance-fields";
import { CompensationPayBasisFields } from "../pages/compensation-pay-basis-fields";
import { CompensationTaxFields } from "../pages/compensation-tax-fields";
import { FixedEarningFields } from "./fixed-earning-fields";
export function CompensationEditorScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const currentMonth = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 7),
    [props.timezone],
  );
  const month = parameters.get("month") ?? currentMonth;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`/payroll/compensation?${compensationEmployeeParameters(props.access.companyId)}`}
        replace
      />
    );
  const canonical = compensationReviewParameters(props.access.companyId, month);
  if (!parameters.has("company") || !parameters.has("month"))
    return (
      <Navigate
        to={`/payroll/compensation/${encodeURIComponent(employeeId)}/edit?${canonical}`}
        replace
      />
    );
  return (
    <CompensationEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${month}`}
      {...props}
      employee={employeeId}
      month={month}
      backTo={`/payroll/compensation/${encodeURIComponent(employeeId)}?${canonical}`}
    />
  );
}
function CompensationEditorBinding(
  props: PayrollScreenProps & { employee: string; month: string; backTo: string },
) {
  const { payroll, access, employee, month, nextIdentifier, locale } = props;
  const controller = useMemo(
    () => new CompensationEditorController(payroll, access, employee, month, nextIdentifier),
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
  const form = useCompensationForm(controller, state, month);
  useWorkspaceRevalidation(state.failure);
  return (
    <CompensationEditorPage
      state={state}
      form={form}
      locale={locale}
      companyName={props.companyName}
      backTo={props.backTo}
      onRetry={controller.retrySave}
    >
      <CompensationEarningsFields form={form} failure={state.failure} locale={locale}>
        {form.earnings.map((line, index) => (
          <FixedEarningFields
            key={line.id}
            control={form.control}
            index={index}
            editable={form.editable}
            failure={state.failure}
            locale={locale}
            onRemove={form.removeEarning}
          />
        ))}
      </CompensationEarningsFields>
      <CompensationTaxFields form={form} failure={state.failure} locale={locale} />
      <CompensationInsuranceFields form={form} failure={state.failure} locale={locale} />
      <CompensationDeductionFields form={form} failure={state.failure} locale={locale} />
      <CompensationPayBasisFields form={form} failure={state.failure} locale={locale} />
    </CompensationEditorPage>
  );
}
