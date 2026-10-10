import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canEditApprovalDelegation } from "../../domain/policies/approval-delegation-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalDelegationController } from "../controllers/approval-delegation-controller";
import { approvalDelegationView } from "../models/approval-delegation-view";
import { approvalParameters } from "../models/approval-route";
import { ApprovalDelegationPage } from "../pages/approval-delegation-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalDelegationScreen(props: Props) {
  const { delegationId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = approvalParameters(
    props.access.companyId,
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null,
  ).toString();
  const backTo = `/approvals/delegations?${query}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`/approvals/delegations/${encodeURIComponent(delegationId)}?${query}`}
        replace
      />
    );
  return (
    <ApprovalDelegationBinding
      key={`${props.accountId}:${props.access.companyId}:${delegationId}`}
      {...props}
      id={delegationId}
      backTo={backTo}
      editTo={`/approvals/delegations/${encodeURIComponent(delegationId)}/edit?${query}`}
    />
  );
}
function ApprovalDelegationBinding({
  accountId,
  access,
  approvals,
  companyName,
  timezone,
  locale,
  id,
  backTo,
  editTo,
}: Props & {
  id: string;
  backTo: string;
  editTo: string;
}) {
  const controller = useMemo(
    () => new ApprovalDelegationController(approvals.loadDelegation, access, accountId, id),
    [approvals.loadDelegation, access, accountId, id],
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
    () => (state.delegation ? approvalDelegationView(state.delegation, locale, timezone) : null),
    [state.delegation, locale, timezone],
  );
  return (
    <ApprovalDelegationPage
      state={state}
      properties={view}
      companyName={companyName}
      locale={locale}
      backTo={backTo}
      editTo={
        state.delegation && canEditApprovalDelegation(access, accountId, state.delegation)
          ? editTo
          : null
      }
      onRefresh={controller.refresh}
    />
  );
}
