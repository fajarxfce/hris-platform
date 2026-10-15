import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canActOnAmendmentReview } from "../../domain/policies/payroll-amendment-review-policy";
import type { PayrollScreenProps } from "../contracts/payroll-screen-props";
import { PayrollAmendmentReader } from "../controllers/payroll-amendment-reader";
import { payrollAmendmentPublicationsPath } from "../models/payroll-amendment-publication-route";
import {
  payrollAmendmentReviewActionPath,
  payrollAmendmentReviewPath,
  payrollAmendmentReviewsPath,
} from "../models/payroll-amendment-review-route";
import { payrollAmendmentReviewView } from "../models/payroll-amendment-review-view";
import { payrollAmendmentPath } from "../models/payroll-amendment-route";
import { PayrollAmendmentReviewPage } from "../pages/payroll-amendment-review-page";

export function PayrollAmendmentReviewScreen(props: PayrollScreenProps) {
  const { reviewId = "" } = useParams(),
    [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/payroll/amendments?company=${props.access.companyId}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={payrollAmendmentReviewPath(props.access.companyId, reviewId, parameters)}
        replace
      />
    );
  return (
    <PayrollAmendmentReviewBinding
      key={`${props.accountId}:${props.access.companyId}:${reviewId}`}
      {...props}
      id={reviewId}
    />
  );
}
function PayrollAmendmentReviewBinding(props: PayrollScreenProps & { id: string }) {
  const { payroll, access, id, locale } = props,
    [parameters, setParameters] = useSearchParams(),
    navigate = useNavigate(),
    controller = useMemo(
      () => new PayrollAmendmentReader(payroll.loadAmendmentReview, access, id),
      [payroll.loadAmendmentReview, access, id],
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
  const view = useMemo(
      () => (state.data ? payrollAmendmentReviewView(state.data, locale) : null),
      [state.data, locale],
    ),
    tab =
      ["overview", "stages", "history"].find((value) => value === parameters.get("tab")) ??
      "overview",
    proposal = new URLSearchParams(parameters),
    basis = new URLSearchParams(parameters);
  for (const query of [proposal, basis]) {
    query.delete("historyAfter");
    query.delete("assessment");
    if (state.data) query.set("revision", String(state.data.review.amendmentVersion));
  }
  proposal.set("tab", "proposal");
  basis.set("tab", "calculation");
  return (
    <PayrollAmendmentReviewPage
      state={state}
      view={view}
      locale={locale}
      companyName={props.companyName}
      publicationsTo={
        state.data
          ? payrollAmendmentPublicationsPath(access.companyId, state.data.amendment.id, parameters)
          : null
      }
      backTo={
        state.data
          ? payrollAmendmentReviewsPath(access.companyId, state.data.amendment.id, parameters)
          : null
      }
      proposalTo={
        state.data
          ? payrollAmendmentPath(access.companyId, state.data.amendment.id, proposal)
          : null
      }
      basisTo={
        state.data ? payrollAmendmentPath(access.companyId, state.data.amendment.id, basis) : null
      }
      actionLinks={
        state.data?.availableActions
          .filter((action) => canActOnAmendmentReview(access.permissions, action))
          .map((action) => ({
            action,
            to: payrollAmendmentReviewActionPath(access.companyId, id, action, parameters),
          })) ?? []
      }
      tab={tab}
      onRefresh={controller.refresh}
      onTab={(value) => {
        if (!["overview", "stages", "history"].includes(value)) return;
        const next = new URLSearchParams(parameters);
        next.set("tab", value);
        setParameters(next, { replace: true });
      }}
      onRevision={(value) => {
        if (!state.data?.history.some((change) => String(change.amendmentRevision) === value))
          return;
        const next = new URLSearchParams(proposal);
        next.set("revision", value);
        navigate(payrollAmendmentPath(access.companyId, state.data.amendment.id, next));
      }}
    />
  );
}
