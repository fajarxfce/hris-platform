import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollFinalizationController } from "../controllers/payroll-finalization-controller";
import {
  payrollFinalizationPath,
  payrollFinalizationsPath,
} from "../models/payroll-finalization-route";
import { payrollFinalizationView } from "../models/payroll-finalization-view";
import { PayrollFinalizationPage } from "../pages/payroll-finalization-page";
export function PayrollFinalizationScreen(props: PayrollScreenProps) {
  const { finalizationId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/publications?company=${props.access.companyId}`} replace />;
  if (!params.has("company"))
    return (
      <Navigate to={payrollFinalizationPath(props.access.companyId, finalizationId)} replace />
    );
  return (
    <PayrollFinalizationBinding
      key={`${props.accountId}:${props.access.companyId}:${finalizationId}`}
      {...props}
      id={finalizationId}
    />
  );
}
function PayrollFinalizationBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    visible = useContext(WorkspaceVisibilityContext);
  const controller = useMemo(
    () => new PayrollFinalizationController(payroll.loadFinalization, access, id),
    [payroll.loadFinalization, access, id],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    const changed = () => controller.setVisible(visible && document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, [controller, visible]);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
      () => (state.details ? payrollFinalizationView(state.details, locale) : null),
      [state.details, locale],
    ),
    canViewJob =
      !state.failure &&
      state.details &&
      (state.details.finalization.actorId === props.accountId ||
        access.permissions.includes("jobs.read"));
  return (
    <PayrollFinalizationPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={
        state.details ? payrollFinalizationsPath(access.companyId, state.details.run.id) : null
      }
      jobTo={
        canViewJob && state.details
          ? `/administration/jobs?${new URLSearchParams({ company: access.companyId, job: state.details.job.jobId })}`
          : null
      }
      onRefresh={controller.refresh}
    />
  );
}
