import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { TaxOpeningEditorController } from "../controllers/tax-opening-editor-controller";
import { useTaxOpeningForm } from "../controllers/use-tax-opening-form";
import { taxOpeningReviewParameters } from "../models/tax-opening-route";
import { TaxOpeningEditorPage } from "../pages/tax-opening-editor-page";
export function TaxOpeningEditorScreen(props: PayrollScreenProps) {
  const [parameters] = useSearchParams(),
    { employeeId = "" } = useParams();
  const today = useMemo(
    () => companyDate(props.timezone, new Date()).slice(0, 4),
    [props.timezone],
  );
  const year = parameters.get("year") ?? today;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/tax-openings?company=${props.access.companyId}`} replace />;
  const query = taxOpeningReviewParameters(props.access.companyId, year);
  if (!parameters.has("company") || !parameters.has("year"))
    return (
      <Navigate
        to={`/payroll/tax-openings/${encodeURIComponent(employeeId)}/edit?${query}`}
        replace
      />
    );
  return (
    <TaxOpeningEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${year}`}
      {...props}
      employee={employeeId}
      year={year}
      backTo={`/payroll/tax-openings/${encodeURIComponent(employeeId)}?${query}`}
    />
  );
}
function TaxOpeningEditorBinding(
  props: PayrollScreenProps & { employee: string; year: string; backTo: string },
) {
  const { payroll, access, employee, year, nextIdentifier, locale } = props;
  const controller = useMemo(
    () => new TaxOpeningEditorController(payroll, access, employee, year, nextIdentifier),
    [payroll, access, employee, year, nextIdentifier],
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
  const form = useTaxOpeningForm(controller, state);
  return (
    <TaxOpeningEditorPage
      state={state}
      form={form}
      locale={locale}
      companyName={props.companyName}
      backTo={props.backTo}
      onRetry={controller.retrySave}
    />
  );
}
