import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollReviewSubmissionController } from "../controllers/payroll-review-submission-controller";
import { usePayrollReviewReasonForm } from "../controllers/use-payroll-review-reason-form";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import {
  payrollReviewPath,
  payrollReviewSubmissionPath,
  payrollReviewsPath,
} from "../models/payroll-review-route";
import { payrollReviewPreparationView } from "../models/payroll-review-view";
import { PayrollReviewCommandPage } from "../pages/payroll-review-command-page";
export function PayrollReviewSubmissionScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return <Navigate to={payrollReviewSubmissionPath(props.access.companyId, runId)} replace />;
  return (
    <PayrollReviewSubmissionBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
    />
  );
}
function PayrollReviewSubmissionBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale, nextIdentifier } = props,
    controller = useMemo(
      () => new PayrollReviewSubmissionController(payroll, access, id, nextIdentifier),
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
  const form = usePayrollReviewReasonForm(controller, state),
    text = payrollReviewMessages(locale),
    properties = useMemo(
      () => (state.review ? payrollReviewPreparationView(state.review, locale) : []),
      [state.review, locale],
    );
  return (
    <PayrollReviewCommandPage
      state={state}
      form={form}
      title={text.submission}
      note={text.submissionNote}
      label={text.submit}
      canSubmit={state.review?.availableActions.includes("SUBMIT") ?? false}
      requiredReason
      properties={properties}
      issues={state.review?.blockingIssues ?? []}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollReviewsPath(access.companyId, id)}
      backLabel={text.back}
      successTo={state.receipt ? payrollReviewPath(access.companyId, state.receipt.id) : null}
      successMessage={text.submittedMessage}
      onRetry={controller.retry}
    />
  );
}
