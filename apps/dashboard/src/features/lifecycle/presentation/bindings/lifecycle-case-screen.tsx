import { useEffect, useId, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCaseController } from "../controllers/lifecycle-case-controller";
import { lifecycleCaseParameters, lifecycleCaseSearch } from "../models/lifecycle-case-route";
import { lifecycleCaseView, lifecycleTaskView } from "../models/lifecycle-case-view";
import { LifecycleCasePage } from "../pages/lifecycle-case-page";
import { LifecycleHistoryBinding } from "./lifecycle-history-binding";
import { LifecycleTaskAssignmentBinding } from "./lifecycle-task-assignment-binding";
import { LifecycleTaskBinding } from "./lifecycle-task-binding";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function LifecycleCaseScreen(props: Props) {
  const { caseId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const search = lifecycleCaseSearch(parameters, props.access.companyId);
  const base = lifecycleCaseParameters(props.access.companyId, search);
  const backTo = `/people/lifecycle/cases?${base}`;
  const tab = parameters.get("tab") === "history" ? "history" : "overview";
  const after = parameters.get("historyAfter");
  const canonical = new URLSearchParams(base);
  if (tab === "history") canonical.set("tab", "history");
  if (after !== null) canonical.set("historyAfter", after);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company") || !parameters.has("status"))
    return (
      <Navigate to={`/people/lifecycle/cases/${encodeURIComponent(caseId)}?${canonical}`} replace />
    );
  return (
    <LifecycleCaseBinding
      key={`${props.accountId}:${props.access.companyId}:${caseId}`}
      {...props}
      id={caseId}
      backTo={backTo}
      tab={tab}
      historyAfter={after}
      onTab={(next) => {
        if (next !== "overview" && next !== "history") return;
        const query = new URLSearchParams(canonical);
        query.set("tab", next);
        setParameters(query);
      }}
      onHistoryPage={(next) => {
        const query = new URLSearchParams(canonical);
        if (next === null) query.delete("historyAfter");
        else query.set("historyAfter", next);
        setParameters(query);
      }}
    />
  );
}
function LifecycleCaseBinding(
  props: Props & {
    id: string;
    backTo: string;
    tab: string;
    historyAfter: string | null;
    onTab: (tab: string) => void;
    onHistoryPage: (after: string | null) => void;
  },
) {
  const tabId = useId();
  const controller = useMemo(
    () => new LifecycleCaseController(props.lifecycle.loadCase, props.access, props.id),
    [props.lifecycle.loadCase, props.access, props.id],
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
    () => (state.case ? lifecycleCaseView(state.case, props.locale, props.timezone) : null),
    [state.case, props.locale, props.timezone],
  );
  const task = useMemo(
    () =>
      state.selectedTask
        ? lifecycleTaskView(state.selectedTask, props.accountId, props.locale, props.timezone)
        : null,
    [state.selectedTask, props.accountId, props.locale, props.timezone],
  );
  return (
    <>
      <LifecycleCasePage
        state={state}
        view={view}
        companyName={props.companyName}
        locale={props.locale}
        backTo={props.backTo}
        tab={props.tab}
        tabId={tabId}
        onTab={props.onTab}
        onRefresh={controller.refresh}
        onOpenTask={controller.openTask}
        history={
          state.case && props.tab === "history" ? (
            <LifecycleHistoryBinding
              load={props.lifecycle.loadHistory}
              access={props.access}
              id={state.case.id}
              after={props.historyAfter}
              locale={props.locale}
              timezone={props.timezone}
              onPage={props.onHistoryPage}
              onScopeFailure={controller.reportScopeFailure}
            />
          ) : null
        }
      />
      {state.selectedTask &&
        task &&
        (state.taskPanel === "assignment" ? (
          <LifecycleTaskAssignmentBinding
            accountId={props.accountId}
            access={props.access}
            actions={props.lifecycle}
            context={state.selectedTask}
            locale={props.locale}
            nextIdentifier={props.nextIdentifier}
            onClose={controller.closeTask}
            onBack={controller.showTaskDetails}
            onReload={() => {
              controller.closeTask();
              void controller.refresh();
            }}
          />
        ) : (
          <LifecycleTaskBinding
            accountId={props.accountId}
            access={props.access}
            change={props.lifecycle.changeTask}
            context={state.selectedTask}
            properties={task}
            locale={props.locale}
            nextIdentifier={props.nextIdentifier}
            onClose={controller.closeTask}
            onAssign={controller.openTaskAssignment}
            onReload={() => {
              controller.closeTask();
              void controller.refresh();
            }}
          />
        ))}
    </>
  );
}
