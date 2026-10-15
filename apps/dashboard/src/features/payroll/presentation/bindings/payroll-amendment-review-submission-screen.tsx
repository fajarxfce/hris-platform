import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentReviewSubmissionController } from "../controllers/payroll-amendment-review-submission-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollAmendmentReviewMessages } from "../i18n/payroll-amendment-review-messages";
import {
  payrollAmendmentReviewPath,
  payrollAmendmentReviewSubmissionPath,
  payrollAmendmentReviewsPath,
} from "../models/payroll-amendment-review-route";
import { payrollAmendmentReviewPreparationView } from "../models/payroll-amendment-review-view";
import { PayrollCommandPage } from "../pages/payroll-command-page";

export function PayrollAmendmentReviewSubmissionScreen(props: PayrollScreenProps) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentReviewSubmissionPath(props.access.companyId, amendmentId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentReviewSubmissionBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentReviewSubmissionBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale, nextIdentifier } = props,
    [parameters] = useSearchParams(),
    controller = useMemo(
      () => new PayrollAmendmentReviewSubmissionController(payroll, access, id, nextIdentifier),
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
  const form = usePayrollReasonForm(controller, state),
    text = payrollAmendmentReviewMessages(locale),
    properties = useMemo(
      () => payrollAmendmentReviewPreparationView(state.review, locale),
      [state.review, locale],
    );
  return (
    <PayrollCommandPage
      state={state}
      form={form}
      title={text.submission}
      note={text.submissionNote}
      label={text.submit}
      canSubmit={state.review?.availableActions.includes("SUBMIT") ?? false}
      requiredReason
      properties={properties}
      issues={[]}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollAmendmentReviewsPath(access.companyId, id, parameters)}
      backLabel={text.back}
      successTo={
        state.reviewId
          ? payrollAmendmentReviewPath(access.companyId, state.reviewId, parameters)
          : null
      }
      successMessage={text.submitted}
      successLabel={text.open}
      onRetry={controller.retry}
    />
  );
}
