import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { ExpensePolicyController } from "../controllers/expense-policy-controller";
import { expensePolicyParameters, expensePolicyQuery } from "../models/expense-policy-route";
import { expensePolicyRevisionView, expensePolicyView } from "../models/expense-policy-view";
import { ExpensePolicyPage } from "../pages/expense-policy-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  expense: ExpenseUseCases;
  companyName: string;
  locale: Locale;
  timezone: string;
};
export function ExpensePolicyScreen(props: Props) {
  const { policyId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const directory = expensePolicyParameters(
    props.access.companyId,
    expensePolicyQuery(parameters, props.access.companyId),
  );
  const backTo = `/expenses/policies?${directory}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  const historyAfter = parameters.get("historyAfter");
  if (!parameters.has("company")) {
    if (historyAfter !== null) directory.set("historyAfter", historyAfter);
    return (
      <Navigate to={`/expenses/policies/${encodeURIComponent(policyId)}?${directory}`} replace />
    );
  }
  return (
    <ExpensePolicyBinding
      key={`${props.accountId}:${props.access.companyId}:${policyId}`}
      {...props}
      id={policyId}
      historyAfter={historyAfter}
      backTo={backTo}
      editTo={`/expenses/policies/${encodeURIComponent(policyId)}/edit?${directory}`}
      onHistory={(after) => {
        const next = new URLSearchParams(directory);
        if (after !== null) next.set("historyAfter", after);
        setParameters(next);
      }}
    />
  );
}
function ExpensePolicyBinding({
  expense,
  access,
  id,
  historyAfter,
  locale,
  timezone,
  companyName,
  backTo,
  editTo,
  onHistory,
}: Props & {
  id: string;
  historyAfter: string | null;
  backTo: string;
  editTo: string;
  onHistory: (after: string | null) => void;
}) {
  const controller = useMemo(
    () => new ExpensePolicyController(expense.loadPolicy, access, id, historyAfter),
    [expense.loadPolicy, access, id, historyAfter],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  const revisionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.selectedRevision) {
      revisionRef.current?.focus({ preventScroll: true });
      revisionRef.current?.scrollIntoView({ block: "start" });
    }
  }, [state.selectedRevision]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.review ? expensePolicyView(state.review, locale, timezone) : null),
    [state.review, locale, timezone],
  );
  const selected = useMemo(
    () =>
      state.selectedRevision
        ? expensePolicyRevisionView(state.selectedRevision, locale, timezone)
        : null,
    [state.selectedRevision, locale, timezone],
  );
  return (
    <ExpensePolicyPage
      state={state}
      view={view}
      selected={selected}
      revisionRef={revisionRef}
      companyName={companyName}
      locale={locale}
      timezone={timezone}
      backTo={backTo}
      firstHistory={historyAfter === null}
      editTo={state.review ? editTo : null}
      onRefresh={controller.refresh}
      onFirst={() => onHistory(null)}
      onNext={() => {
        if (state.review?.history.nextCursor) onHistory(state.review.history.nextCursor);
      }}
      onRevision={controller.selectRevision}
    />
  );
}
