import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { AssignedLifecycleTasksController } from "../controllers/assigned-lifecycle-tasks-controller";
import { assignedLifecycleTasksView, lifecycleTaskView } from "../models/lifecycle-case-view";
import { lifecycleAfter, lifecycleParameters } from "../models/lifecycle-route";
import { AssignedLifecycleTasksPage } from "../pages/assigned-lifecycle-tasks-page";
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
export function AssignedLifecycleTasksScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const after = lifecycleAfter(parameters, props.access.companyId);
  const canonical = lifecycleParameters(props.access.companyId, after);
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/lifecycle/tasks?${canonical}`} replace />;
  return (
    <AssignedLifecycleTasksBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      onPage={(next) => setParameters(lifecycleParameters(props.access.companyId, next))}
    />
  );
}
function AssignedLifecycleTasksBinding(
  props: Props & { after: string | null; onPage: (after: string | null) => void },
) {
  const controller = useMemo(
    () =>
      new AssignedLifecycleTasksController(
        props.lifecycle.loadAssignedTasks,
        props.access,
        props.accountId,
        props.after,
      ),
    [props.lifecycle.loadAssignedTasks, props.access, props.accountId, props.after],
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
  const rows = useMemo(
    () => (state.page ? assignedLifecycleTasksView(state.page, props.locale) : []),
    [state.page, props.locale],
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
      <AssignedLifecycleTasksPage
        state={state}
        rows={rows}
        companyName={props.companyName}
        locale={props.locale}
        firstPage={props.after === null}
        onRefresh={controller.refresh}
        onFirst={() => props.onPage(null)}
        onNext={() => {
          if (state.page?.nextCursor) props.onPage(state.page.nextCursor);
        }}
        onOpen={controller.openTask}
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
