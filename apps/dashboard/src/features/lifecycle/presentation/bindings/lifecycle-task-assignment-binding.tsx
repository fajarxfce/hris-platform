import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTaskAssignmentController } from "../controllers/lifecycle-task-assignment-controller";
import { useLifecycleTaskAssignmentForm } from "../controllers/use-lifecycle-task-assignment-form";
import { lifecycleTaskAssignmentView } from "../models/lifecycle-case-view";
import { LifecycleTaskAssignmentPage } from "../pages/lifecycle-task-assignment-page";
import { LifecycleAssigneePicker } from "./lifecycle-assignee-picker";

export function LifecycleTaskAssignmentBinding(props: {
  accountId: AccountId;
  access: CompanyAccess;
  actions: Pick<LifecycleUseCases, "assignTask" | "loadAssignees">;
  context: LifecycleTaskContext;
  locale: Locale;
  nextIdentifier: () => string;
  onClose: () => void;
  onReload: () => void;
  onBack: () => void;
}) {
  const controller = useMemo(
    () =>
      new LifecycleTaskAssignmentController(
        props.actions.assignTask,
        props.access,
        props.context,
        props.nextIdentifier,
      ),
    [props.actions.assignTask, props.access, props.context, props.nextIdentifier],
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
  const form = useLifecycleTaskAssignmentForm(
    controller,
    state,
    props.onClose,
    props.onReload,
    props.onBack,
  );
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.stage === "saved") props.onReload();
  }, [state.stage, props.onReload]);
  const view = useMemo(
    () => lifecycleTaskAssignmentView(props.context, props.accountId, props.locale),
    [props.context, props.accountId, props.locale],
  );
  return (
    <>
      <LifecycleTaskAssignmentPage
        title={props.context.task.title}
        properties={view}
        form={form}
        state={state}
        locale={props.locale}
        canRemove={props.context.task.assigneeId !== null}
        onRetry={controller.retry}
      />
      {form.picker && form.editable && (
        <LifecycleAssigneePicker
          accountId={props.accountId}
          access={props.access}
          load={props.actions.loadAssignees}
          excluded={props.context.task.assigneeId}
          locale={props.locale}
          onSelect={form.select}
          onClose={form.closePicker}
        />
      )}
    </>
  );
}
