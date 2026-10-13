import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { ExpenseScreenProps } from "../contracts/expense-screen-props";
import { ExpenseDecisionController } from "../controllers/expense-decision-controller";
import { useExpenseDecisionForm } from "../controllers/use-expense-decision-form";
import { expenseSubmissionPath } from "../models/expense-claim-route";
import {
  type ExpenseDecisionIntent,
  expenseDecisionForIntent,
  expenseDecisionIntents,
} from "../models/expense-decision-intent";
import { expenseSubmissionView } from "../models/expense-submission-view";
import { ExpenseDecisionPage } from "../pages/expense-decision-page";
import { ExpenseEvidenceDownloads } from "./expense-evidence-downloads";

type Props = ExpenseScreenProps & { nextIdentifier: () => string };
export function ExpenseDecisionScreen(props: Props) {
  const { submissionId = "", intent: inputIntent = "" } = useParams();
  const [parameters] = useSearchParams();
  const intent = expenseDecisionIntents.find((value) => value === inputIntent);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals?company=${props.access.companyId}`} replace />;
  const query = new URLSearchParams(parameters);
  query.set("company", props.access.companyId);
  query.delete("view");
  query.delete("line");
  query.delete("review");
  const backTo = `${expenseSubmissionPath(submissionId)}?${query}`;
  if (!intent) return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return <Navigate to={`${expenseSubmissionPath(submissionId)}/${intent}?${query}`} replace />;
  return (
    <ExpenseDecisionBinding
      key={`${props.accountId}:${props.access.companyId}:${submissionId}:${intent}`}
      {...props}
      id={submissionId}
      intent={intent}
      backTo={backTo}
    />
  );
}
function ExpenseDecisionBinding(
  props: Props & { id: string; intent: ExpenseDecisionIntent; backTo: string },
) {
  const controller = useMemo(
    () =>
      new ExpenseDecisionController(
        props.expense,
        props.access,
        props.id,
        expenseDecisionForIntent[props.intent],
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
  const form = useExpenseDecisionForm(controller, state);
  const view = useMemo(
    () =>
      state.review
        ? expenseSubmissionView(
            state.review,
            state.selectedLineId,
            null,
            props.locale,
            props.timezone,
          )
        : null,
    [state.review, state.selectedLineId, props.locale, props.timezone],
  );
  return (
    <ExpenseDecisionPage
      state={state}
      form={form}
      view={view}
      intent={props.intent}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      onRetry={controller.retry}
      onLine={controller.selectLine}
      receiptContent={
        view?.line && state.review ? (
          <ExpenseEvidenceDownloads
            key={state.selectedLineId}
            download={props.expense.downloadReceipt}
            access={props.access}
            submission={state.review}
            rows={view.line.receipts}
            locale={props.locale}
            disabled={!form.editable}
            onUnavailable={controller.revokeEvidence}
          />
        ) : null
      }
    />
  );
}
