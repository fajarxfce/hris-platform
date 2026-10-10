import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTaskEditorController } from "../controllers/lifecycle-task-editor-controller";
import { useLifecycleTaskForm } from "../controllers/use-lifecycle-task-form";
import { LifecycleTaskPanel } from "../pages/lifecycle-task-panel";

export function LifecycleTaskBinding({
  accountId,
  access,
  change,
  context,
  properties,
  locale,
  nextIdentifier,
  onClose,
  onReload,
}: {
  accountId: AccountId;
  access: CompanyAccess;
  change: LifecycleUseCases["changeTask"];
  context: LifecycleTaskContext;
  properties: readonly Readonly<{ label: string; value: string }>[];
  locale: Locale;
  nextIdentifier: () => string;
  onClose: () => void;
  onReload: () => void;
}) {
  const controller = useMemo(
    () => new LifecycleTaskEditorController(change, access, accountId, context, nextIdentifier),
    [change, access, accountId, context, nextIdentifier],
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
  const form = useLifecycleTaskForm(controller, state, onClose, onReload);
  useWorkspaceRevalidation(state.failure);
  useEffect(() => {
    if (state.stage === "saved") onReload();
  }, [state.stage, onReload]);
  return (
    <LifecycleTaskPanel
      title={context.task.title}
      properties={properties}
      locale={locale}
      statuses={controller.statuses}
      state={state}
      form={form}
      onRetry={controller.retry}
    />
  );
}
