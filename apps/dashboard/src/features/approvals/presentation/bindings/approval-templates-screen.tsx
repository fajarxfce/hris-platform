import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateSearch } from "../../domain/entities/approval-template";
import { canManageApprovals } from "../../domain/policies/approval-read-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalTemplatesController } from "../controllers/approval-templates-controller";
import { useApprovalTemplateFilters } from "../controllers/use-approval-template-filters";
import {
  approvalTemplateParameters,
  approvalTemplateSearch,
} from "../models/approval-template-route";
import { approvalTemplatesView } from "../models/approval-template-view";
import { ApprovalTemplatesPage } from "../pages/approval-templates-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalTemplatesScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = useMemo(
    () => approvalTemplateSearch(parameters, props.access.companyId, today),
    [parameters, props.access.companyId, today],
  );
  const pinned = approvalTemplateParameters(props.access.companyId, search).toString();
  if (
    parameters.get("company") !== props.access.companyId ||
    !parameters.has("kind") ||
    !parameters.has("asOf")
  )
    return <Navigate to={`/approvals/templates?${pinned}`} replace />;
  return (
    <ApprovalTemplatesBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      search={search}
      parameters={pinned}
      onSearch={(next) => setParameters(approvalTemplateParameters(props.access.companyId, next))}
      onOpen={(id) => navigate(`/approvals/templates/${encodeURIComponent(id)}?${pinned}`)}
    />
  );
}
function ApprovalTemplatesBinding({
  access,
  approvals,
  search,
  parameters,
  companyName,
  locale,
  onSearch,
  onOpen,
}: Props & {
  search: ApprovalTemplateSearch;
  parameters: string;
  onSearch: (search: ApprovalTemplateSearch) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new ApprovalTemplatesController(approvals.loadTemplates, access, search),
    [approvals.loadTemplates, access, search],
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
  const filters = useApprovalTemplateFilters(search, onSearch);
  const rows = useMemo(
    () => (state.page ? approvalTemplatesView(state.page, locale) : []),
    [state.page, locale],
  );
  return (
    <ApprovalTemplatesPage
      state={state}
      rows={rows}
      filters={filters}
      companyName={companyName}
      locale={locale}
      firstPage={search.after === null}
      createTo={
        canManageApprovals(access.permissions) ? `/approvals/templates/new?${parameters}` : null
      }
      onRefresh={controller.refresh}
      onFirst={() => onSearch({ ...search, after: null })}
      onNext={() => {
        if (state.page?.nextCursor) onSearch({ ...search, after: state.page.nextCursor });
      }}
      onOpen={onOpen}
    />
  );
}
