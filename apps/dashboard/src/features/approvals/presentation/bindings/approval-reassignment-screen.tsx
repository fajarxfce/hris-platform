import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalReassignmentController } from "../controllers/approval-reassignment-controller";
import { useApprovalReassignmentForm } from "../controllers/use-approval-reassignment-form";
import { approvalReassignmentView } from "../models/approval-reassignment-view";
import { approvalParameters } from "../models/approval-route";
import { ApprovalReassignmentPage } from "../pages/approval-reassignment-page";
import { ApprovalAssigneePicker } from "./approval-assignee-picker";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
};
export function ApprovalReassignmentScreen(props: Props) {
  const { approvalId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = approvalParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals?${query}`} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate to={`/approvals/${encodeURIComponent(approvalId)}/reassign?${query}`} replace />
    );
  return (
    <ApprovalReassignmentBinding
      key={`${props.accountId}:${props.access.companyId}:${approvalId}:${query}`}
      {...props}
      id={approvalId}
      backTo={`/approvals/${encodeURIComponent(approvalId)}?${query}`}
    />
  );
}
function ApprovalReassignmentBinding(props: Props & { id: string; backTo: string }) {
  const { approvals, access, id, nextIdentifier, locale } = props;
  const controller = useMemo(
    () => new ApprovalReassignmentController(approvals, access, id, nextIdentifier),
    [approvals, access, id, nextIdentifier],
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
  const form = useApprovalReassignmentForm(controller, state);
  const properties = useMemo(
    () => (state.request ? approvalReassignmentView(state.request, locale) : null),
    [state.request, locale],
  );
  return (
    <>
      <ApprovalReassignmentPage
        state={state}
        form={form}
        properties={properties}
        companyName={props.companyName}
        locale={locale}
        backTo={props.backTo}
        onRetry={controller.retry}
      />
      {form.picker && state.request && (
        <ApprovalAssigneePicker
          access={access}
          approvals={approvals}
          kind={state.request.kind}
          selected={form.excluded}
          locale={locale}
          onSelect={form.select}
          onDismiss={form.closePicker}
        />
      )}
    </>
  );
}
