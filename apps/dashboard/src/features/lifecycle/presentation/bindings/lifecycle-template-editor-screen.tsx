import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadLifecycle } from "../../domain/policies/lifecycle-template-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTemplateEditorController } from "../controllers/lifecycle-template-editor-controller";
import { useLifecycleTemplateForm } from "../controllers/use-lifecycle-template-form";
import { lifecycleAfter, lifecycleParameters } from "../models/lifecycle-route";
import { LifecycleTemplateEditorPage } from "../pages/lifecycle-template-editor-page";
import { LifecycleTaskFields } from "./lifecycle-task-fields";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  locale: Locale;
  creating: boolean;
  nextIdentifier: () => string;
};
export function LifecycleTemplateEditorScreen(props: Props) {
  const { templateId = "" } = useParams();
  const [parameters] = useSearchParams();
  const pinned = lifecycleParameters(
    props.access.companyId,
    lifecycleAfter(parameters, props.access.companyId),
  ).toString();
  const backTo = canReadLifecycle(props.access.permissions)
    ? `/people/lifecycle/templates?${pinned}`
    : "/";
  const path = props.creating
    ? "/people/lifecycle/templates/new"
    : `/people/lifecycle/templates/${encodeURIComponent(templateId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <LifecycleTemplateEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}`}
      {...props}
      templateId={templateId}
      backTo={backTo}
      parameters={pinned}
    />
  );
}
function LifecycleTemplateEditorBinding(
  props: Props & { templateId: string; backTo: string; parameters: string },
) {
  const { creating, nextIdentifier, templateId, lifecycle, access } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : templateId),
    [creating, nextIdentifier, templateId],
  );
  const controller = useMemo(
    () => new LifecycleTemplateEditorController(lifecycle, access, creating, id, nextIdentifier),
    [lifecycle, access, creating, id, nextIdentifier],
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
  const form = useLifecycleTemplateForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  return (
    <LifecycleTemplateEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={props.backTo}
      savedTo={
        state.receipt && canReadLifecycle(access.permissions)
          ? `/people/lifecycle/templates/${state.receipt.id}?${props.parameters}`
          : null
      }
      onRetry={controller.retrySave}
    >
      {form.tasks.map((task, index) => (
        <LifecycleTaskFields
          key={task.id}
          index={index}
          control={form.control}
          editable={form.editable}
          canRemove={form.canRemove}
          locale={props.locale}
          onRemove={form.removeTask}
        />
      ))}
    </LifecycleTemplateEditorPage>
  );
}
