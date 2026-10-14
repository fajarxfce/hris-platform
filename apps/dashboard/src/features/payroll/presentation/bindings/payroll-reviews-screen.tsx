import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollReviewsController } from "../controllers/payroll-reviews-controller";
import { payrollRunPath } from "../models/payroll-calculation-route";
import {
  payrollReviewPath,
  payrollReviewSubmissionPath,
  payrollReviewsPath,
} from "../models/payroll-review-route";
import { payrollReviewPreparationView, payrollReviewRows } from "../models/payroll-review-view";
import { PayrollReviewsPage } from "../pages/payroll-reviews-page";
export function PayrollReviewsScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return <Navigate to={payrollReviewsPath(props.access.companyId, runId)} replace />;
  return (
    <PayrollReviewsBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
    />
  );
}
function PayrollReviewsBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollReviewsController(payroll.loadReviews, access, id),
      [payroll.loadReviews, access, id],
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
    () => (state.data ? payrollReviewPreparationView(state.data.preparation, locale) : []),
    [state.data, locale],
  );
  const rows = useMemo(
    () =>
      payrollReviewRows(
        state.data?.reviews ?? [],
        locale,
        state.data?.preparation.run.timezone ?? props.timezone,
      ),
    [state.data, locale, props.timezone],
  );
  return (
    <PayrollReviewsPage
      state={state}
      properties={properties}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollRunPath(access.companyId, id)}
      submitTo={
        state.data?.preparation.availableActions.includes("SUBMIT")
          ? payrollReviewSubmissionPath(access.companyId, id)
          : null
      }
      onRefresh={controller.refresh}
      onReview={(review) => navigate(payrollReviewPath(access.companyId, review))}
    />
  );
}
