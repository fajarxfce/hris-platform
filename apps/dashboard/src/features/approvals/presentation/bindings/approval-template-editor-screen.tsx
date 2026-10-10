import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import { companyDate } from "../../../../core/presentation/dates/company-date";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import { isApprovalKind } from "../../domain/policies/approval-template-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalTemplateEditorController } from "../controllers/approval-template-editor-controller";
import { useApprovalTemplateForm } from "../controllers/use-approval-template-form";
import {
  approvalTemplateParameters,
  approvalTemplateSearch,
} from "../models/approval-template-route";
import { ApprovalTemplateEditorPage } from "../pages/approval-template-editor-page";
import { ApprovalStageFields } from "./approval-stage-fields";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  creating: boolean;
  nextIdentifier: () => string;
};
export function ApprovalTemplateEditorScreen(props: Props) {
  const { templateId = "" } = useParams();
  const [parameters] = useSearchParams();
  const today = useMemo(() => companyDate(props.timezone, new Date()), [props.timezone]);
  const search = approvalTemplateSearch(parameters, props.access.companyId, today);
  const pinned = approvalTemplateParameters(props.access.companyId, search).toString();
  const path = props.creating
    ? "/approvals/templates/new"
    : `/approvals/templates/${encodeURIComponent(templateId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals/templates?${pinned}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <ApprovalTemplateEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${pinned}`}
      {...props}
      templateId={templateId}
      parameters={pinned}
      initialKind={isApprovalKind(search.kind) ? search.kind : "LEAVE"}
      today={today}
    />
  );
}
function ApprovalTemplateEditorBinding(
  props: Props & {
    templateId: string;
    parameters: string;
    initialKind: ApprovalKind;
    today: string;
  },
) {
  const { creating, nextIdentifier, templateId, approvals, access } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : templateId),
    [creating, nextIdentifier, templateId],
  );
  const controller = useMemo(
    () => new ApprovalTemplateEditorController(approvals, access, creating, id, nextIdentifier),
    [approvals, access, creating, id, nextIdentifier],
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
  const form = useApprovalTemplateForm(controller, state, props.initialKind, props.today);
  useWorkspaceRevalidation(state.failure);
  return (
    <ApprovalTemplateEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/approvals/templates?${props.parameters}`}
      savedTo={
        state.receipt ? `/approvals/templates/${state.receipt.id}?${props.parameters}` : null
      }
      onRetry={controller.retrySave}
    >
      {form.stages.map((stage, index) => (
        <ApprovalStageFields
          key={stage.id}
          control={form.control}
          index={index}
          count={form.stages.length}
          kind={form.kind.value}
          editable={form.editable}
          access={access}
          approvals={approvals}
          locale={props.locale}
          onRemove={form.removeStage}
          onMove={form.moveStage}
        />
      ))}
    </ApprovalTemplateEditorPage>
  );
}
