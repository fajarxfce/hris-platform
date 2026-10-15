import { useContext, useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canPublishPayrollAmendments } from "../../domain/policies/payroll-amendment-publication-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentPublicationController } from "../controllers/payroll-amendment-publication-controller";
import {
  payrollAmendmentPublicationPath,
  payrollAmendmentPublicationRecoveryPath,
  payrollAmendmentPublicationsPath,
} from "../models/payroll-amendment-publication-route";
import { payrollAmendmentPublicationView } from "../models/payroll-amendment-publication-view";
import { payrollAmendmentReviewPath } from "../models/payroll-amendment-review-route";
import { payrollAmendmentPath } from "../models/payroll-amendment-route";
import { PayrollAmendmentPublicationPage } from "../pages/payroll-amendment-publication-page";

export function PayrollAmendmentPublicationScreen(props: PayrollScreenProps) {
  const { amendmentId = "", publicationId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentPublicationPath(
          props.access.companyId,
          amendmentId,
          publicationId,
          parameters,
        )}
        replace
      />
    );
  return (
    <PayrollAmendmentPublicationBinding
      key={`${props.accountId}:${props.access.companyId}:${amendmentId}:${publicationId}`}
      {...props}
      amendmentId={amendmentId}
      id={publicationId}
    />
  );
}
function PayrollAmendmentPublicationBinding(
  props: PayrollScreenProps & { amendmentId: string; id: string },
) {
  const { payroll, access, amendmentId, id, locale } = props,
    [parameters] = useSearchParams(),
    visible = useContext(WorkspaceVisibilityContext),
    controller = useMemo(
      () =>
        new PayrollAmendmentPublicationController(
          payroll.loadAmendmentPublication,
          access,
          amendmentId,
          id,
        ),
      [payroll.loadAmendmentPublication, access, amendmentId, id],
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
      () => (state.details ? payrollAmendmentPublicationView(state.details, locale) : null),
      [state.details, locale],
    ),
    acquired = state.failure ? null : state.details,
    proposal = new URLSearchParams(parameters),
    basis = new URLSearchParams(parameters),
    review = new URLSearchParams(parameters);
  for (const query of [proposal, basis]) {
    query.delete("historyAfter");
    query.delete("assessment");
    if (acquired) query.set("revision", String(acquired.publication.amendmentVersion));
  }
  proposal.set("tab", "proposal");
  basis.set("tab", "calculation");
  review.set("tab", "history");
  return (
    <PayrollAmendmentPublicationPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      backTo={
        acquired
          ? payrollAmendmentPublicationsPath(access.companyId, amendmentId, parameters)
          : null
      }
      recoverTo={
        acquired?.availableActions.includes("RECOVER") &&
        canPublishPayrollAmendments(access.permissions)
          ? payrollAmendmentPublicationRecoveryPath(access.companyId, amendmentId, id, parameters)
          : null
      }
      proposalTo={acquired ? payrollAmendmentPath(access.companyId, amendmentId, proposal) : null}
      calculationTo={acquired ? payrollAmendmentPath(access.companyId, amendmentId, basis) : null}
      reviewTo={
        acquired
          ? payrollAmendmentReviewPath(access.companyId, acquired.publication.reviewId, review)
          : null
      }
      jobTo={
        acquired &&
        (acquired.publication.actorId === props.accountId ||
          access.permissions.includes("jobs.read"))
          ? `/administration/jobs?${new URLSearchParams({ company: access.companyId, job: acquired.job.jobId })}`
          : null
      }
      onRefresh={controller.refresh}
    />
  );
}
