import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseSearch } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { LifecycleCasesController } from "../controllers/lifecycle-cases-controller";
import { lifecycleCaseParameters, lifecycleCaseSearch } from "../models/lifecycle-case-route";
import { lifecycleCasesView } from "../models/lifecycle-case-view";
import { LifecycleCasesPage } from "../pages/lifecycle-cases-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  locale: Locale;
};
export function LifecycleCasesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const search = useMemo(
    () => lifecycleCaseSearch(parameters, props.access.companyId),
    [parameters, props.access.companyId],
  );
  const canonical = lifecycleCaseParameters(props.access.companyId, search).toString();
  if (parameters.get("company") !== props.access.companyId || !parameters.has("status"))
    return <Navigate to={`/people/lifecycle/cases?${canonical}`} replace />;
  return (
    <LifecycleCasesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      onSearch={(next) => setParameters(lifecycleCaseParameters(props.access.companyId, next))}
      onOpen={(id) => navigate(`/people/lifecycle/cases/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function LifecycleCasesBinding({
  access,
  lifecycle,
  companyName,
  locale,
  search,
  onSearch,
  onOpen,
}: Props & {
  search: LifecycleCaseSearch;
  onSearch: (search: LifecycleCaseSearch) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new LifecycleCasesController(lifecycle.loadCases, access, search),
    [lifecycle.loadCases, access, search],
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
    () => (state.page ? lifecycleCasesView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <LifecycleCasesPage
      state={state}
      rows={rows}
      search={search}
      companyName={companyName}
      locale={locale}
      templatesTo={`/people/lifecycle/templates?company=${access.companyId}`}
      onStatus={(status) => onSearch({ ...search, status, after: null })}
      onClearEmployee={() => onSearch({ ...search, employmentId: null, after: null })}
      onRefresh={controller.refresh}
      onFirst={() => onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
