import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollRunController } from "../controllers/payroll-run-controller";
import {
  payrollCalculationsPath,
  payrollRunEmployeePath,
  payrollRunPath,
} from "../models/payroll-calculation-route";
import { payrollRunView } from "../models/payroll-calculation-view";
import { payrollReviewsPath } from "../models/payroll-review-route";
import { PayrollRunPage } from "../pages/payroll-run-page";
export function PayrollRunScreen(props: PayrollScreenProps) {
  const { runId = "" } = useParams(),
    [params] = useSearchParams();
  if (params.has("company") && params.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/periods?company=${props.access.companyId}`} replace />;
  const after = params.get("after"),
    tab = ["attempts", "sources"].includes(params.get("tab") ?? "")
      ? (params.get("tab") ?? "results")
      : "results";
  if (!params.has("company"))
    return <Navigate to={payrollRunPath(props.access.companyId, runId, after, tab)} replace />;
  return (
    <PayrollRunBinding
      key={`${props.accountId}:${props.access.companyId}:${runId}`}
      {...props}
      id={runId}
      after={after}
      tab={tab}
    />
  );
}
function PayrollRunBinding(
  props: PayrollScreenProps & { id: string; after: string | null; tab: string },
) {
  const { payroll, access, id, after, tab, locale } = props,
    navigate = useNavigate(),
    visible = useContext(WorkspaceVisibilityContext),
    controller = useMemo(
      () => new PayrollRunController(payroll.loadRun, access, id, after),
      [payroll.loadRun, access, id, after],
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
      () => (state.details ? payrollRunView(state.details, locale) : null),
      [state.details, locale],
    ),
    canAct = !state.failure && state.stage !== "loading",
    canViewJob =
      state.details &&
      (state.details.run.actorId === props.accountId ||
        access.permissions.includes("jobs.read") ||
        access.permissions.includes("jobs.manage"));
  return (
    <PayrollRunPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={
        state.details ? payrollCalculationsPath(access.companyId, state.details.run.periodId) : null
      }
      jobTo={
        canAct && canViewJob && state.details
          ? `/administration/jobs?${new URLSearchParams({ company: access.companyId, job: state.details.job.jobId })}`
          : null
      }
      reviewsTo={state.details ? payrollReviewsPath(access.companyId, id) : null}
      resumeTo={
        canAct && state.details?.availableActions.includes("RESUME")
          ? `/payroll/runs/${encodeURIComponent(id)}/resume?company=${access.companyId}`
          : null
      }
      abandonTo={
        canAct && state.details?.availableActions.includes("ABANDON")
          ? `/payroll/runs/${encodeURIComponent(id)}/abandon?company=${access.companyId}`
          : null
      }
      tab={tab}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onTab={(next) => {
        if (["results", "attempts", "sources"].includes(next))
          navigate(payrollRunPath(access.companyId, id, after, next));
      }}
      onEmployee={(employee) =>
        navigate(payrollRunEmployeePath(access.companyId, id, employee, after))
      }
      onFirst={() => navigate(payrollRunPath(access.companyId, id, null, tab))}
      onNext={() => {
        if (state.details?.results.nextCursor)
          navigate(payrollRunPath(access.companyId, id, state.details.results.nextCursor, tab));
      }}
    />
  );
}
