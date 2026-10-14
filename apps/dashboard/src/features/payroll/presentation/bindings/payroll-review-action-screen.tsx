import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollReviewAction } from "../../domain/entities/payroll-review-command";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollReviewActionController } from "../controllers/payroll-review-action-controller";
import { usePayrollReviewReasonForm } from "../controllers/use-payroll-review-reason-form";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import { payrollReviewActionPath, payrollReviewPath } from "../models/payroll-review-route";
import { payrollReviewView } from "../models/payroll-review-view";
import { PayrollReviewCommandPage } from "../pages/payroll-review-command-page";
export function PayrollReviewActionScreen(
  props: PayrollScreenProps & { action: PayrollReviewAction },
) {
  const { reviewId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return (
      <Navigate
        to={payrollReviewActionPath(props.access.companyId, reviewId, props.action)}
        replace
      />
    );
  return (
    <PayrollReviewActionBinding
      key={`${props.accountId}:${props.access.companyId}:${reviewId}:${props.action}`}
      {...props}
      id={reviewId}
    />
  );
}
function PayrollReviewActionBinding(
  props: PayrollScreenProps & { id: string; action: PayrollReviewAction },
) {
  const { payroll, access, id, action, locale, nextIdentifier } = props,
    controller = useMemo(
      () => new PayrollReviewActionController(payroll, access, id, action, nextIdentifier),
      [payroll, access, id, action, nextIdentifier],
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
  const form = usePayrollReviewReasonForm(controller, state, action !== "APPROVE"),
    text = payrollReviewMessages(locale),
    properties = useMemo(() => {
      if (!state.review) return [];
      const view = payrollReviewView(state.review, locale);
      return [...view.properties, ...view.totals];
    }, [state.review, locale]);
  const backTo = payrollReviewPath(access.companyId, id);
  return (
    <PayrollReviewCommandPage
      state={state}
      form={form}
      title={text[action]}
      note={text[`${action}_NOTE`]}
      label={text[action]}
      canSubmit={state.review?.availableActions.includes(action) ?? false}
      requiredReason={action !== "APPROVE"}
      properties={properties}
      issues={[]}
      locale={locale}
      companyName={props.companyName}
      backTo={backTo}
      backLabel={text.openReview}
      successTo={backTo}
      successMessage={action === "WITHDRAW" ? text.withdrawnMessage : text.decidedMessage}
      onRetry={controller.retry}
    />
  );
}
