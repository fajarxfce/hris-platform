import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { TaxOpeningVerificationController } from "../controllers/tax-opening-verification-controller";
import { useTaxOpeningVerification } from "../controllers/use-tax-opening-verification";
import { taxOpeningReviewParameters } from "../models/tax-opening-route";
import { taxOpeningView } from "../models/tax-opening-view";
import { TaxOpeningVerificationPage } from "../pages/tax-opening-verification-page";
export function TaxOpeningVerificationScreen(props: PayrollScreenProps) {
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
        to={`/payroll/tax-openings/${encodeURIComponent(employeeId)}/verify?${query}`}
        replace
      />
    );
  return (
    <TaxOpeningVerificationBinding
      key={`${props.accountId}:${props.access.companyId}:${employeeId}:${year}`}
      {...props}
      employee={employeeId}
      year={year}
      backTo={`/payroll/tax-openings/${encodeURIComponent(employeeId)}?${query}`}
    />
  );
}
function TaxOpeningVerificationBinding(
  props: PayrollScreenProps & { employee: string; year: string; backTo: string },
) {
  const { payroll, access, employee, year, nextIdentifier, locale, timezone } = props;
  const controller = useMemo(
    () => new TaxOpeningVerificationController(payroll, access, employee, year, nextIdentifier),
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
  const form = useTaxOpeningVerification(controller, state);
  const view = useMemo(
    () => (state.context?.current ? taxOpeningView(state.context.current, locale, timezone) : null),
    [state.context, locale, timezone],
  );
  return (
    <TaxOpeningVerificationPage
      state={state}
      form={form}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={props.backTo}
      onRetry={controller.retryVerification}
    />
  );
}
