import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageLifecycle } from "../../domain/policies/lifecycle-template-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleTemplatesController } from "../controllers/lifecycle-templates-controller";
import { lifecycleAfter, lifecycleParameters } from "../models/lifecycle-route";
import { lifecycleTemplatesView } from "../models/lifecycle-template-view";
import { LifecycleTemplatesPage } from "../pages/lifecycle-templates-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  locale: Locale;
};
export function LifecycleTemplatesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after = lifecycleAfter(parameters, props.access.companyId);
  const pinned = lifecycleParameters(props.access.companyId, after).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/people/lifecycle/templates?${pinned}`} replace />;
  return (
    <LifecycleTemplatesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      parameters={pinned}
      onPage={(next) => setParameters(lifecycleParameters(props.access.companyId, next))}
      onOpen={(id) => navigate(`/people/lifecycle/templates/${encodeURIComponent(id)}?${pinned}`)}
    />
  );
}
function LifecycleTemplatesBinding({
  access,
  lifecycle,
  companyName,
  locale,
  after,
  parameters,
  onPage,
  onOpen,
}: Props & {
  after: string | null;
  parameters: string;
  onPage: (after: string | null) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LifecycleTemplatesController(lifecycle.loadTemplates, access, after),
    [lifecycle.loadTemplates, access, after],
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
    () => (state.page ? lifecycleTemplatesView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <LifecycleTemplatesPage
      state={state}
      rows={rows}
      companyName={companyName}
      locale={locale}
      firstPage={after === null}
      createTo={
        canManageLifecycle(access.permissions)
          ? `/people/lifecycle/templates/new?${parameters}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={onOpen}
    />
  );
}
