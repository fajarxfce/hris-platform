import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentPublicationRecoveryController } from "../controllers/payroll-amendment-publication-recovery-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollAmendmentPublicationMessages } from "../i18n/payroll-amendment-publication-messages";
import {
  payrollAmendmentPublicationPath,
  payrollAmendmentPublicationRecoveryPath,
} from "../models/payroll-amendment-publication-route";
import { payrollAmendmentPublicationView } from "../models/payroll-amendment-publication-view";
import { PayrollCommandPage } from "../pages/payroll-command-page";

export function PayrollAmendmentPublicationRecoveryScreen(props: PayrollScreenProps) {
  const { amendmentId = "", publicationId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentPublicationRecoveryPath(
          props.access.companyId,
          amendmentId,
          publicationId,
          parameters,
        )}
        replace
      />
    );
  return (
    <PayrollAmendmentPublicationRecoveryBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}:${publicationId}`}
      {...props}
      amendmentId={amendmentId}
      id={publicationId}
    />
  );
}
function PayrollAmendmentPublicationRecoveryBinding(
  props: PayrollScreenProps & { amendmentId: string; id: string },
) {
  const { payroll, access, amendmentId, id, locale, nextIdentifier } = props,
    [parameters] = useSearchParams(),
    controller = useMemo(
      () =>
        new PayrollAmendmentPublicationRecoveryController(
          payroll,
          access,
          amendmentId,
          id,
          nextIdentifier,
        ),
      [payroll, access, amendmentId, id, nextIdentifier],
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
    text = payrollAmendmentPublicationMessages(locale),
    properties = useMemo(() => {
      if (!state.review) return [];
      const view = payrollAmendmentPublicationView(state.review, locale);
      return [...view.properties, ...view.basis, ...view.progress];
    }, [state.review, locale]),
    backTo = payrollAmendmentPublicationPath(access.companyId, amendmentId, id, parameters);
  return (
    <PayrollCommandPage
      state={state}
      form={form}
      title={text.recovery}
      note={text.recoveryNote}
      label={text.recovery}
      canSubmit={state.review?.availableActions.includes("RECOVER") ?? false}
      requiredReason
      properties={properties}
      issues={[]}
      locale={locale}
      companyName={props.companyName}
      backTo={backTo}
      backLabel={text.open}
      successTo={state.receipt ? backTo : null}
      successMessage={text.recovered}
      successLabel={text.open}
      onRetry={controller.retry}
    />
  );
}
