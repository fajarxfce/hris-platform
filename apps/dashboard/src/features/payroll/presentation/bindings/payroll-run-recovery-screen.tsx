import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollCalculationRecoveryAction } from "../../domain/entities/payroll-calculation-command";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollRunRecoveryController } from "../controllers/payroll-run-recovery-controller";
import { usePayrollRunRecoveryForm } from "../controllers/use-payroll-run-recovery-form";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollRunPath } from "../models/payroll-calculation-route";
import { payrollRunProperties } from "../models/payroll-calculation-view";
import { PayrollRunRecoveryPage } from "../pages/payroll-run-recovery-page";
export function PayrollRunRecoveryScreen(
  props: PayrollScreenProps & { action: PayrollCalculationRecoveryAction },
) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return (
      <Navigate
        to={`/payroll/runs/${encodeURIComponent(runId)}/${props.action}?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <PayrollRunRecoveryBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}:${props.action}`}
      {...props}
      id={runId}
    />
  );
}
function PayrollRunRecoveryBinding(
  props: PayrollScreenProps & { id: string; action: PayrollCalculationRecoveryAction },
) {
  const { payroll, access, id, action, nextIdentifier, locale } = props,
    controller = useMemo(
      () => new PayrollRunRecoveryController(payroll, access, id, action, nextIdentifier),
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
  const form = usePayrollRunRecoveryForm(controller, state),
    properties = useMemo(
      () =>
        state.review
          ? [
              ...payrollRunProperties(state.review.run, locale),
              {
                label: payrollCalculationMessages(locale).periodVersion,
                value: String(state.review.period.version),
              },
            ]
          : [],
      [state.review, locale],
    );
  return (
    <PayrollRunRecoveryPage
      state={state}
      action={action}
      form={form}
      properties={properties}
      locale={locale}
      companyName={props.companyName}
      backTo={payrollRunPath(access.companyId, id)}
      onRetry={controller.retry}
    />
  );
}
