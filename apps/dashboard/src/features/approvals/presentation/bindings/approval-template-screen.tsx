import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalTemplateController } from "../controllers/approval-template-controller";
import {
  approvalTemplateParameters,
  approvalTemplateSearch,
} from "../models/approval-template-route";
import { approvalTemplateView } from "../models/approval-template-view";
import { ApprovalTemplatePage } from "../pages/approval-template-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalTemplateScreen(props: Props) {
  const { templateId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = approvalTemplateSearch(parameters, props.access.companyId, today);
  const pinned = approvalTemplateParameters(props.access.companyId, search).toString();
  const revision = parameters.get("revision");
  const path = `/approvals/templates/${encodeURIComponent(templateId)}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals/templates?${pinned}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${path}?${approvalTemplateParameters(props.access.companyId, search, revision)}`}
        replace
      />
    );
  return (
    <ApprovalTemplateBinding
      key={`${props.accountId}:${props.access.companyId}:${templateId}`}
      {...props}
      templateId={templateId}
      revision={revision}
      path={path}
      parameters={pinned}
    />
  );
}
function ApprovalTemplateBinding({
  access,
  approvals,
  companyName,
  locale,
  templateId,
  revision,
  path,
  parameters,
}: Props & { templateId: string; revision: string | null; path: string; parameters: string }) {
  const controller = useMemo(
    () => new ApprovalTemplateController(approvals.loadTemplate, access, templateId, revision),
    [approvals.loadTemplate, access, templateId, revision],
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
    () => (state.template ? approvalTemplateView(state.template, locale) : null),
    [state.template, locale],
  );
  return (
    <ApprovalTemplatePage
      state={state}
      view={view}
      companyName={companyName}
      locale={locale}
      backTo={`/approvals/templates?${parameters}`}
      editTo={state.template ? `${path}/edit?${parameters}` : null}
      previousTo={
        state.template && state.template.revision > 0
          ? `${path}?${parameters}&revision=${state.template.revision - 1}`
          : null
      }
      nextTo={
        state.template && state.template.revision < state.template.version
          ? `${path}?${parameters}&revision=${state.template.revision + 1}`
          : null
      }
      latestTo={revision !== null ? `${path}?${parameters}` : null}
      onRefresh={controller.refresh}
    />
  );
}
