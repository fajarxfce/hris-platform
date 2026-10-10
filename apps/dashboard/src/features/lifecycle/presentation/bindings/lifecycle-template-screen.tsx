import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageLifecycle } from "../../domain/policies/lifecycle-template-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTemplateController } from "../controllers/lifecycle-template-controller";
import { lifecycleAfter, lifecycleParameters } from "../models/lifecycle-route";
import { lifecycleTemplateView } from "../models/lifecycle-template-view";
import { LifecycleTemplatePage } from "../pages/lifecycle-template-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  locale: Locale;
};
export function LifecycleTemplateScreen(props: Props) {
  const { templateId = "" } = useParams();
  const [parameters] = useSearchParams();
  const pinned = lifecycleParameters(
    props.access.companyId,
    lifecycleAfter(parameters, props.access.companyId),
  ).toString();
  const backTo = `/people/lifecycle/templates?${pinned}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/people/lifecycle/templates/${encodeURIComponent(templateId)}?${pinned}`}
        replace
      />
    );
  return (
    <LifecycleTemplateBinding
      key={`${props.accountId}:${props.access.companyId}:${templateId}`}
      {...props}
      id={templateId}
      backTo={backTo}
      parameters={pinned}
    />
  );
}
function LifecycleTemplateBinding({
  access,
  lifecycle,
  companyName,
  locale,
  id,
  backTo,
  parameters,
}: Props & {
  id: string;
  backTo: string;
  parameters: string;
}) {
  const controller = useMemo(
    () => new LifecycleTemplateController(lifecycle.loadTemplate, access, id),
    [lifecycle.loadTemplate, access, id],
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
    () => (state.template ? lifecycleTemplateView(state.template, locale) : null),
    [state.template, locale],
  );
  return (
    <LifecycleTemplatePage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      onRefresh={controller.refresh}
      editTo={
        state.template && canManageLifecycle(access.permissions)
          ? `/people/lifecycle/templates/${state.template.id}/edit?${parameters}`
          : null
      }
    />
  );
}
