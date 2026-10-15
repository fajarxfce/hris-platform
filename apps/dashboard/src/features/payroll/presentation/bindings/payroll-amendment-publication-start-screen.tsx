import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentPublicationStartController } from "../controllers/payroll-amendment-publication-start-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollAmendmentPublicationMessages } from "../i18n/payroll-amendment-publication-messages";
import {
  payrollAmendmentPublicationPath,
  payrollAmendmentPublicationStartPath,
  payrollAmendmentPublicationsPath,
} from "../models/payroll-amendment-publication-route";
import { payrollAmendmentPublicationPreparationView } from "../models/payroll-amendment-publication-view";
import { PayrollCommandPage } from "../pages/payroll-command-page";

export function PayrollAmendmentPublicationStartScreen(props: PayrollScreenProps) {
  const { amendmentId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentPublicationStartPath(props.access.companyId, amendmentId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentPublicationStartBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}`}
      {...props}
      id={amendmentId}
    />
  );
}
function PayrollAmendmentPublicationStartBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale, nextIdentifier } = props,
    [parameters] = useSearchParams(),
    controller = useMemo(
      () => new PayrollAmendmentPublicationStartController(payroll, access, id, nextIdentifier),
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
    text = payrollAmendmentPublicationMessages(locale),
    properties = useMemo(
      () => payrollAmendmentPublicationPreparationView(state.review, locale),
      [state.review, locale],
    );
  return (
    <PayrollCommandPage
      state={state}
      form={form}
      title={text.start}
      note={text.startNote}
      label={text.start}
      canSubmit={state.review !== null}
      requiredReason
      properties={properties}
      issues={[]}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollAmendmentPublicationsPath(access.companyId, id, parameters)}
      backLabel={text.back}
      successTo={
        state.publicationId
          ? payrollAmendmentPublicationPath(access.companyId, id, state.publicationId, parameters)
          : null
      }
      successMessage={text.requested}
      successLabel={text.open}
      onRetry={controller.retry}
    />
  );
}
