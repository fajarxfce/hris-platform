import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollFinalizationStartController } from "../controllers/payroll-finalization-start-controller";
import { usePayrollReasonForm } from "../controllers/use-payroll-reason-form";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import {
  payrollFinalizationPath,
  payrollFinalizationStartPath,
  payrollFinalizationsPath,
} from "../models/payroll-finalization-route";
import { payrollFinalizationPreparationView } from "../models/payroll-finalization-view";
import { PayrollCommandPage } from "../pages/payroll-command-page";
export function PayrollFinalizationStartScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/publications?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return <Navigate to={payrollFinalizationStartPath(props.access.companyId, runId)} replace />;
  return (
    <PayrollFinalizationStartBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
    />
  );
}
function PayrollFinalizationStartBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale, nextIdentifier } = props;
  const controller = useMemo(
    () => new PayrollFinalizationStartController(payroll, access, id, nextIdentifier),
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
    text = payrollFinalizationMessages(locale),
    properties = useMemo(
      () => (state.review ? payrollFinalizationPreparationView(state.review, locale) : []),
      [state.review, locale],
    );
  return (
    <PayrollCommandPage
      state={state}
      form={form}
      title={text.reviewPublication}
      note={text.publicationNote}
      label={text.publish}
      canSubmit={state.review?.availableActions.includes("START") ?? false}
      requiredReason
      properties={properties}
      issues={state.review?.blockingIssues ?? []}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollFinalizationsPath(access.companyId, id)}
      backLabel={text.overview}
      successTo={state.receipt ? payrollFinalizationPath(access.companyId, state.receipt.id) : null}
      successLabel={text.openPublication}
      successMessage={text.publicationRequested}
      onRetry={controller.retry}
    />
  );
}
