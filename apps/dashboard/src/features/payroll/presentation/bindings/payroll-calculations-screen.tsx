import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollCalculationsController } from "../controllers/payroll-calculations-controller";
import { payrollCalculationsPath, payrollRunPath } from "../models/payroll-calculation-route";
import { payrollCalculationReviewView, payrollRunRows } from "../models/payroll-calculation-view";
import { payrollPeriodProperties } from "../models/payroll-period-view";
import { PayrollCalculationsPage } from "../pages/payroll-calculations-page";
export function PayrollCalculationsScreen(props: PayrollScreenProps) {
  const { periodId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return <Navigate to={payrollCalculationsPath(props.access.companyId, periodId)} replace />;
  return (
    <PayrollCalculationsBinding
      key={`${props.accountId}:${props.access.companyId}:${periodId}`}
      {...props}
      id={periodId}
    />
  );
}
function PayrollCalculationsBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollCalculationsController(payroll.loadCalculations, access, id),
      [payroll.loadCalculations, access, id],
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
  const properties = useMemo(
      () =>
        state.data
          ? state.data.review.period.status === "DRAFT"
            ? payrollCalculationReviewView(state.data.review, locale)
            : payrollPeriodProperties(state.data.review.period, locale)
          : [],
      [state.data, locale],
    ),
    rows = useMemo(
      () =>
        payrollRunRows(
          state.data?.runs ?? [],
          locale,
          state.data?.review.period.timezone ?? props.timezone,
        ),
      [state.data, locale, props.timezone],
    );
  return (
    <PayrollCalculationsPage
      state={state}
      properties={properties}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      backTo={`/payroll/periods/${encodeURIComponent(id)}?company=${access.companyId}`}
      startTo={
        state.data?.review.availableActions.includes("START")
          ? `/payroll/periods/${encodeURIComponent(id)}/calculate?company=${access.companyId}`
          : null
      }
      onRefresh={controller.refresh}
      onRun={(id) => navigate(payrollRunPath(access.companyId, id))}
    />
  );
}
