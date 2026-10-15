import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollAmendmentReviewAction } from "../../domain/entities/payroll-amendment-review-details";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentReviewActionController } from "../controllers/payroll-amendment-review-action-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollAmendmentReviewMessages } from "../i18n/payroll-amendment-review-messages";
import {
  payrollAmendmentReviewActionPath,
  payrollAmendmentReviewPath,
} from "../models/payroll-amendment-review-route";
import { payrollAmendmentReviewView } from "../models/payroll-amendment-review-view";
import { PayrollCommandPage } from "../pages/payroll-command-page";

export function PayrollAmendmentReviewActionScreen(
  props: PayrollScreenProps & { action: PayrollAmendmentReviewAction },
) {
  const { reviewId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentReviewActionPath(
          props.access.companyId,
          reviewId,
          props.action,
          parameters,
        )}
        replace
      />
    );
  return (
    <PayrollAmendmentReviewActionBinding
      key={`${props.accountId}:${props.access.companyId}:${reviewId}:${props.action}`}
      {...props}
      id={reviewId}
    />
  );
}
function PayrollAmendmentReviewActionBinding(
  props: PayrollScreenProps & { id: string; action: PayrollAmendmentReviewAction },
) {
  const { payroll, access, id, action, locale, nextIdentifier } = props,
    [parameters] = useSearchParams(),
    controller = useMemo(
      () => new PayrollAmendmentReviewActionController(payroll, access, id, action, nextIdentifier),
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
  const form = usePayrollReasonForm(controller, state),
    text = payrollAmendmentReviewMessages(locale),
    properties = useMemo(() => {
      if (!state.review) return [];
      const view = payrollAmendmentReviewView(state.review, locale);
      return [...view.properties, ...view.totals];
    }, [state.review, locale]),
    back = new URLSearchParams(parameters);
  back.delete("tab");
  const backTo = payrollAmendmentReviewPath(access.companyId, id, back);
  return (
    <PayrollCommandPage
      state={state}
      form={form}
      title={text[action]}
      note={text[`${action}_NOTE`]}
      label={text[action]}
      canSubmit={state.review?.availableActions.includes(action) ?? false}
      requiredReason
      properties={properties}
      issues={[]}
      locale={locale}
      companyName={props.companyName}
      backTo={backTo}
      backLabel={text.open}
      successTo={state.receipt ? backTo : null}
      successMessage={action === "WITHDRAW" ? text.withdrawn : text.decided}
      successLabel={text.open}
      onRetry={controller.retry}
    />
  );
}
