import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollAmendmentDraftAction } from "../../domain/entities/payroll-amendment-command";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentActionController } from "../controllers/payroll-amendment-action-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollAmendmentActionMessages } from "../i18n/payroll-amendment-action-messages";
import { payrollAmendmentActionView } from "../models/payroll-amendment-action-view";
import {
  payrollAmendmentActionPath,
  payrollAmendmentPath,
} from "../models/payroll-amendment-route";
import { PayrollCommandPage } from "../pages/payroll-command-page";

export function PayrollAmendmentActionScreen(
  props: PayrollScreenProps & { action: PayrollAmendmentDraftAction },
) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentActionPath(
          props.access.companyId,
          amendmentId,
          props.action,
          parameters,
        )}
        replace
      />
    );
  return (
    <PayrollAmendmentActionBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}:${props.action}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentActionBinding(
  props: PayrollScreenProps & { id: string; action: PayrollAmendmentDraftAction },
) {
  const { payroll, access, id, action, locale, nextIdentifier } = props,
    [parameters] = useSearchParams(),
    controller = useMemo(
      () => new PayrollAmendmentActionController(payroll, access, id, action, nextIdentifier),
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
    text = payrollAmendmentActionMessages(locale),
    properties = useMemo(
      () => payrollAmendmentActionView(state.review, locale),
      [state.review, locale],
    );
  const back = new URLSearchParams(parameters);
  for (const key of ["revision", "historyAfter", "assessment"]) back.delete(key);
  back.set("tab", action === "CALCULATE" ? "calculation" : "overview");
  const saved = new URLSearchParams(back);
  if (state.receipt) saved.set("revision", String(state.receipt.version));
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
      backTo={payrollAmendmentPath(access.companyId, id, back)}
      backLabel={text.open}
      successTo={state.receipt ? payrollAmendmentPath(access.companyId, id, saved) : null}
      successMessage={text[`${action}_SAVED`]}
      successLabel={text.open}
      onRetry={controller.retry}
    />
  );
}
