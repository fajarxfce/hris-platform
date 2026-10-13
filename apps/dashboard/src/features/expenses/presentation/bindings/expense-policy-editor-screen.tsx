import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePolicyEditorController } from "../controllers/expense-policy-editor-controller";
import { useExpensePolicyForm } from "../controllers/use-expense-policy-form";
import { expensePolicyParameters, expensePolicyQuery } from "../models/expense-policy-route";
import { ExpensePolicyEditorPage } from "../pages/expense-policy-editor-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  expense: ExpenseUseCases;
  companyName: string;
  locale: Locale;
  timezone: string;
  creating: boolean;
  nextIdentifier: () => string;
};
export function ExpensePolicyEditorScreen(props: Props) {
  const { policyId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const directory = expensePolicyParameters(
    props.access.companyId,
    expensePolicyQuery(parameters, props.access.companyId),
  ).toString();
  const path = props.creating
    ? "/expenses/policies/new"
    : `/expenses/policies/${encodeURIComponent(policyId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/expenses/policies?${directory}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${directory}`} replace />;
  return (
    <ExpensePolicyEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${directory}`}
      {...props}
      policyId={policyId}
      parameters={directory}
      today={today}
    />
  );
}
function ExpensePolicyEditorBinding(
  props: Props & { policyId: string; parameters: string; today: string },
) {
  const { creating, nextIdentifier, policyId, expense, access } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : policyId),
    [creating, nextIdentifier, policyId],
  );
  const controller = useMemo(
    () => new ExpensePolicyEditorController(expense, access, creating, id, nextIdentifier),
    [expense, access, creating, id, nextIdentifier],
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
  const form = useExpensePolicyForm(controller, state, props.today);
  useWorkspaceRevalidation(state.failure);
  return (
    <ExpensePolicyEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/expenses/policies?${props.parameters}`}
      savedTo={state.receipt ? `/expenses/policies/${state.receipt.id}?${props.parameters}` : null}
      onRetry={controller.retrySave}
    />
  );
}
