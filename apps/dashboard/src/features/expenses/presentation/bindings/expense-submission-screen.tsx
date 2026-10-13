import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import {
  canBrowseCompanyExpenses,
  canReadExpenseClaims,
} from "../../domain/policies/expense-claim-read-policy";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpenseSubmissionController } from "../controllers/expense-submission-controller";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import {
  expenseClaimParameters,
  expenseClaimPath,
  expenseClaimSearchFromRoute,
  expenseSubmissionPath,
} from "../models/expense-claim-route";
import { expenseSubmissionView } from "../models/expense-submission-view";
import { ExpenseSubmissionPage } from "../pages/expense-submission-page";
export function ExpenseSubmissionScreen(props: ExpenseScreenProps) {
  const { submissionId = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${canBrowseCompanyExpenses(props.access.permissions) ? "/expenses/claims" : "/approvals"}?company=${props.access.companyId}`}
        replace
      />
    );
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${expenseSubmissionPath(submissionId)}?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <ExpenseSubmissionBinding
      key={`${props.accountId}:${props.access.companyId}:${submissionId}`}
      {...props}
      id={submissionId}
    />
  );
}
function ExpenseSubmissionBinding(props: ExpenseScreenProps & { id: string }) {
  const [parameters, setParameters] = useSearchParams();
  const controller = useMemo(
    () =>
      new ExpenseSubmissionController(
        props.expense.loadSubmission,
        props.expense.downloadReceipt,
        props.access,
        props.id,
      ),
    [props.expense.loadSubmission, props.expense.downloadReceipt, props.access, props.id],
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
  useWorkspaceRevalidation(state.failure ?? state.download.failure);
  const selectedTab = parameters.get("view"),
    tab = selectedTab === "reviews" || selectedTab === "approval" ? selectedTab : "items";
  const line = tab === "items" ? parameters.get("line") : null,
    review = tab === "reviews" ? parameters.get("review") : null;
  const view = useMemo(
    () =>
      state.submission
        ? expenseSubmissionView(state.submission, line, review, props.locale, props.timezone)
        : null,
    [state.submission, line, review, props.locale, props.timezone],
  );
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const directory = expenseClaimParameters(
    props.access.companyId,
    expenseClaimSearchFromRoute(parameters, props.access.companyId, today),
  );
  const sourceClaim =
    state.submission &&
    (canBrowseCompanyExpenses(props.access.permissions) ||
      (canReadExpenseClaims(props.access.permissions) &&
        parameters.get("claim") === state.submission.claimId));
  const approvalAccess = props.access.permissions.includes("approvals.read");
  const text = expenseClaimMessages(props.locale);
  const claimQuery = new URLSearchParams(directory);
  claimQuery.set("view", "submissions");
  const backTo =
    sourceClaim && state.submission
      ? `${expenseClaimPath(state.submission.claimId)}?${claimQuery}`
      : approvalAccess
        ? `/approvals?company=${props.access.companyId}`
        : "/";
  return (
    <ExpenseSubmissionPage
      state={state}
      view={view}
      companyName={props.companyName}
      locale={props.locale}
      tab={tab}
      backTo={backTo}
      backLabel={sourceClaim ? text.viewClaim : approvalAccess ? text.approvals : text.overview}
      approvalTo={
        approvalAccess && state.submission
          ? `/approvals/${encodeURIComponent(state.submission.approval.id)}?company=${props.access.companyId}`
          : null
      }
      onRefresh={controller.refresh}
      onDownload={controller.downloadReceipt}
      onCancelDownload={controller.cancelDownload}
      onTab={(value) => {
        if (!["items", "reviews", "approval"].includes(value)) return;
        controller.cancelDownload();
        const next = new URLSearchParams(parameters);
        next.set("view", value);
        next.delete("line");
        next.delete("review");
        setParameters(next);
      }}
      onLine={(id) => {
        controller.cancelDownload();
        const next = new URLSearchParams(parameters);
        next.delete("review");
        if (id === null) next.delete("line");
        else if (state.submission?.lines.some((item) => item.id === id)) next.set("line", id);
        setParameters(next, { replace: true });
      }}
      onReview={(version) => {
        const next = new URLSearchParams(parameters);
        next.delete("line");
        if (version === null) next.delete("review");
        else if (state.submission?.reviews.some((item) => String(item.claimVersion) === version))
          next.set("review", version);
        setParameters(next, { replace: true });
      }}
    />
  );
}
