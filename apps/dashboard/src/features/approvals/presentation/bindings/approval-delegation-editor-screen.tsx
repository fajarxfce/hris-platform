import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageApprovals } from "../../domain/policies/approval-read-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalDelegationEditorController } from "../controllers/approval-delegation-editor-controller";
import { useApprovalDelegationForm } from "../controllers/use-approval-delegation-form";
import { approvalParameters } from "../models/approval-route";
import { ApprovalDelegationEditorPage } from "../pages/approval-delegation-editor-page";
import { ApprovalAssigneePicker } from "./approval-assignee-picker";

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
export function ApprovalDelegationEditorScreen(props: Props) {
  const { delegationId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = approvalParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  const path = props.creating
    ? "/approvals/delegations/new"
    : `/approvals/delegations/${encodeURIComponent(delegationId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals/delegations?${query}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${query}`} replace />;
  return (
    <ApprovalDelegationEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${query}`}
      {...props}
      delegationId={delegationId}
      query={query}
    />
  );
}
function ApprovalDelegationEditorBinding(props: Props & { delegationId: string; query: string }) {
  const { approvals, access, accountId, creating, nextIdentifier, delegationId } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : delegationId),
    [creating, nextIdentifier, delegationId],
  );
  const now = useMemo(() => new Date().toISOString(), []);
  const controller = useMemo(
    () =>
      new ApprovalDelegationEditorController(
        approvals,
        access,
        accountId,
        creating,
        id,
        nextIdentifier,
      ),
    [approvals, access, accountId, creating, id, nextIdentifier],
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
  const canChooseFrom = creating && canManageApprovals(access.permissions);
  const form = useApprovalDelegationForm(
    controller,
    state,
    accountId,
    props.timezone,
    now,
    canChooseFrom,
  );
  return (
    <>
      <ApprovalDelegationEditorPage
        state={state}
        form={form}
        creating={creating}
        canChooseFrom={canChooseFrom}
        companyName={props.companyName}
        timezone={props.timezone}
        locale={props.locale}
        backTo={`/approvals/delegations?${props.query}`}
        savedTo={state.receipt ? `/approvals/delegations/${state.receipt.id}?${props.query}` : null}
        onRetry={controller.retrySave}
      />
      {form.picker && (
        <ApprovalAssigneePicker
          key={`${form.picker.field}:${form.picker.kind}`}
          access={access}
          approvals={approvals}
          kind={form.picker.kind}
          selected={form.excluded}
          locale={props.locale}
          onSelect={form.select}
          onDismiss={form.closePicker}
        />
      )}
    </>
  );
}
