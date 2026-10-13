import { useMemo } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { expenseClaimActionSnapshot } from "../../domain/entities/expense-claim-action-snapshot";
import { availableExpenseClaimActions } from "../../domain/policies/expense-claim-action-policy";
import {
  canEditExpenseDraft,
  canManageExpenseDrafts,
} from "../../domain/policies/expense-draft-policy";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { useExpenseClaim } from "../controllers/use-expense-claim";
import { expenseClaimIntentForAction } from "../models/expense-claim-action-intent";
import {
  expenseClaimParameters,
  expenseClaimPath,
  expenseClaimSearchFromRoute,
  expenseClaimTab,
  expenseClaimTabs,
  expenseSubmissionPath,
} from "../models/expense-claim-route";
import { expenseClaimView } from "../models/expense-claim-view";
import { ExpenseClaimPage } from "../pages/expense-claim-page";
export function ExpenseClaimScreen(props: ExpenseScreenProps) {
  const { claimId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = expenseClaimSearchFromRoute(parameters, props.access.companyId, today);
  const directory = expenseClaimParameters(props.access.companyId, search);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/claims?${directory}`} replace />;
  if (!parameters.has("company")) {
    if (parameters.has("historyAfter"))
      directory.set("historyAfter", parameters.get("historyAfter") ?? "");
    directory.set("view", expenseClaimTab(parameters.get("view")));
    return <Navigate to={`${expenseClaimPath(claimId)}?${directory}`} replace />;
  }
  return (
    <ExpenseClaimBinding
      key={`${props.accountId}:${props.access.companyId}:${claimId}`}
      {...props}
      id={claimId}
      directory={directory.toString()}
    />
  );
}
function ExpenseClaimBinding(props: ExpenseScreenProps & { id: string; directory: string }) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after = parameters.get("historyAfter");
  const tab = expenseClaimTab(parameters.get("view"));
  const change = parameters.get("change");
  const state = useExpenseClaim(
    props.accountId,
    props.access,
    props.id,
    after,
    props.expense.loadClaim,
  );
  const view = useMemo(
    () =>
      state.review
        ? expenseClaimView(
            state.review,
            tab === "history" ? change : null,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, tab, change, props.locale, props.timezone],
  );
  useWorkspaceRevalidation(state.failure);
  return (
    <ExpenseClaimPage
      state={state}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`/expenses/claims?${props.directory}`}
      editTo={
        state.review &&
        canManageExpenseDrafts(props.access.permissions) &&
        canEditExpenseDraft(state.review.claim)
          ? `${expenseClaimPath(state.review.claim.id)}/edit?${props.directory}`
          : null
      }
      tab={tab}
      firstHistory={after === null}
      actions={
        state.review
          ? availableExpenseClaimActions(
              props.access,
              expenseClaimActionSnapshot(state.review.claim),
            )
          : []
      }
      onAction={(action) => {
        if (
          state.review &&
          availableExpenseClaimActions(
            props.access,
            expenseClaimActionSnapshot(state.review.claim),
          ).includes(action)
        )
          navigate(
            `${expenseClaimPath(state.review.claim.id)}/${expenseClaimIntentForAction[action]}?${props.directory}`,
          );
      }}
      onTab={(value) => {
        if (!expenseClaimTabs.some((item) => item === value)) return;
        const next = new URLSearchParams(parameters);
        next.set("view", value);
        next.delete("change");
        setParameters(next);
      }}
      onLatest={() => {
        const next = new URLSearchParams(parameters);
        next.delete("historyAfter");
        next.delete("change");
        setParameters(next);
      }}
      onOlder={() => {
        if (state.review?.history.nextCursor) {
          const next = new URLSearchParams(parameters);
          next.set("historyAfter", state.review.history.nextCursor);
          next.delete("change");
          setParameters(next);
        }
      }}
      onChange={(version) => {
        const next = new URLSearchParams(parameters);
        if (version === null) next.delete("change");
        else if (state.review?.history.items.some((item) => String(item.version) === version))
          next.set("change", version);
        setParameters(next, { replace: true });
      }}
      onSubmission={(id) => {
        if (!state.review?.submissions.some((item) => item.id === id)) return;
        const next = new URLSearchParams(props.directory);
        next.set("claim", state.review.claim.id);
        navigate(`${expenseSubmissionPath(id)}?${next}`);
      }}
    />
  );
}
