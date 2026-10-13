import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canEditExpenseDraft } from "../../domain/policies/expense-draft-policy";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpenseClaimActionController } from "../controllers/expense-claim-action-controller";
import { useExpenseClaimActionForm } from "../controllers/use-expense-claim-action-form";
import {
  type ExpenseClaimActionIntent,
  expenseClaimActionForIntent,
} from "../models/expense-claim-action-intent";
import { expenseClaimActionView } from "../models/expense-claim-action-view";
import {
  expenseClaimParameters,
  expenseClaimPath,
  expenseClaimSearchFromRoute,
} from "../models/expense-claim-route";
import { ExpenseClaimActionPage } from "../pages/expense-claim-action-page";

type Props = ExpenseScreenProps & { nextIdentifier: () => string };
export function ExpenseClaimActionScreen(props: Props) {
  const { claimId = "", intent: rawIntent = "" } = useParams(),
    [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const query = expenseClaimParameters(
    props.access.companyId,
    expenseClaimSearchFromRoute(parameters, props.access.companyId, today),
  ).toString();
  const intent = (["submit", "cancel", "withdraw"] as const).find((value) => value === rawIntent),
    backTo = `${expenseClaimPath(claimId)}?${query}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/claims?${query}`} replace />;
  if (!intent) return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${expenseClaimPath(claimId)}/${intent}?${query}`} replace />;
  return (
    <ExpenseClaimActionBinding
      key={`${props.accountId}:${props.access.companyId}:${claimId}:${intent}`}
      {...props}
      id={claimId}
      intent={intent}
      backTo={backTo}
      editTo={`${expenseClaimPath(claimId)}/edit?${query}`}
    />
  );
}
function ExpenseClaimActionBinding(
  props: Props & { id: string; intent: ExpenseClaimActionIntent; backTo: string; editTo: string },
) {
  const controller = useMemo(
    () =>
      new ExpenseClaimActionController(
        props.expense,
        props.access,
        props.id,
        expenseClaimActionForIntent[props.intent],
        props.nextIdentifier,
      ),
    [props.expense, props.access, props.id, props.intent, props.nextIdentifier],
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
  const form = useExpenseClaimActionForm(controller, state),
    view = useMemo(
      () =>
        state.review
          ? expenseClaimActionView(state.review, state.selectedLineId, props.locale)
          : null,
      [state.review, state.selectedLineId, props.locale],
    );
  return (
    <ExpenseClaimActionPage
      state={state}
      form={form}
      view={view}
      action={controller.action}
      locale={props.locale}
      companyName={props.companyName}
      backTo={props.backTo}
      editTo={state.review && canEditExpenseDraft(state.review.claim) ? props.editTo : null}
      onRetry={controller.retry}
      onItem={controller.selectLine}
    />
  );
}
