import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canAuthorPayrollAmendments } from "../../domain/policies/payroll-amendment-authoring-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentReader } from "../controllers/payroll-amendment-reader";
import {
  payrollAmendmentReviewPath,
  payrollAmendmentReviewSubmissionPath,
  payrollAmendmentReviewsPath,
} from "../models/payroll-amendment-review-route";
import {
  payrollAmendmentReviewPreparationView,
  payrollAmendmentReviewRows,
} from "../models/payroll-amendment-review-view";
import { payrollAmendmentPath } from "../models/payroll-amendment-route";
import { PayrollAmendmentReviewsPage } from "../pages/payroll-amendment-reviews-page";

export function PayrollAmendmentReviewsScreen(props: PayrollScreenProps) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentReviewsPath(props.access.companyId, amendmentId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentReviewsBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentReviewsBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    [parameters] = useSearchParams(),
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollAmendmentReader(payroll.loadAmendmentReviews, access, id),
      [payroll.loadAmendmentReviews, access, id],
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
      () => payrollAmendmentReviewPreparationView(state.data?.current ?? null, locale),
      [state.data, locale],
    ),
    rows = useMemo(
      () => payrollAmendmentReviewRows(state.data?.reviews ?? [], locale),
      [state.data, locale],
    );
  const back = new URLSearchParams(parameters);
  for (const key of ["revision", "historyAfter", "assessment", "tab"]) back.delete(key);
  return (
    <PayrollAmendmentReviewsPage
      state={state}
      properties={properties}
      rows={rows}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollAmendmentPath(access.companyId, id, back)}
      submitTo={
        canAuthorPayrollAmendments(access.permissions) &&
        state.data?.current.availableActions.includes("SUBMIT")
          ? payrollAmendmentReviewSubmissionPath(access.companyId, id, parameters)
          : null
      }
      onRefresh={controller.refresh}
      onReview={(review) => {
        if (state.data?.reviews.some((value) => value.id === review))
          navigate(payrollAmendmentReviewPath(access.companyId, review, back));
      }}
    />
  );
}
