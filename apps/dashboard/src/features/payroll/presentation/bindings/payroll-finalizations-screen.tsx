import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canReadPayrollPeriods } from "../../domain/policies/payroll-period-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollFinalizationsController } from "../controllers/payroll-finalizations-controller";
import { payrollRunPath } from "../models/payroll-calculation-route";
import {
  payrollFinalizationPath,
  payrollFinalizationStartPath,
  payrollFinalizationsPath,
} from "../models/payroll-finalization-route";
import {
  payrollFinalizationPreparationView,
  payrollFinalizationRows,
} from "../models/payroll-finalization-view";
import { payrollReviewPath } from "../models/payroll-review-route";
import { PayrollFinalizationsPage } from "../pages/payroll-finalizations-page";
export function PayrollFinalizationsScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/publications?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return <Navigate to={payrollFinalizationsPath(props.access.companyId, runId)} replace />;
  return (
    <PayrollFinalizationsBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
    />
  );
}
function PayrollFinalizationsBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    navigate = useNavigate();
  const controller = useMemo(
    () => new PayrollFinalizationsController(payroll.loadFinalizations, access, id),
    [payroll.loadFinalizations, access, id],
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
      () => (state.data ? payrollFinalizationPreparationView(state.data.preparation, locale) : []),
      [state.data, locale],
    ),
    rows = useMemo(
      () =>
        payrollFinalizationRows(
          state.data?.finalizations ?? [],
          locale,
          state.data?.preparation.run.timezone ?? props.timezone,
        ),
      [state.data, locale, props.timezone],
    ),
    issues = useMemo(
      () =>
        state.data?.preparation.blockingIssues.filter(
          (issue) => issue.code !== "payroll_already_finalized",
        ) ?? [],
      [state.data],
    ),
    canReadCalculation = canReadPayrollPeriods(access.permissions),
    review = state.data?.preparation.latestReview;
  return (
    <PayrollFinalizationsPage
      state={state}
      properties={properties}
      rows={rows}
      issues={issues}
      published={state.data?.preparation.run.status === "FINALIZED"}
      locale={locale}
      companyName={props.companyName}
      backTo={`/payroll/publications?company=${access.companyId}`}
      startTo={
        state.data?.preparation.availableActions.includes("START")
          ? payrollFinalizationStartPath(access.companyId, id)
          : null
      }
      runTo={state.data && canReadCalculation ? payrollRunPath(access.companyId, id) : null}
      reviewTo={
        review && canReadCalculation ? payrollReviewPath(access.companyId, review.id) : null
      }
      onRefresh={controller.refresh}
      onOpen={(publication) => {
        if (state.data?.finalizations.some((row) => row.id === publication))
          navigate(payrollFinalizationPath(access.companyId, publication));
      }}
    />
  );
}
