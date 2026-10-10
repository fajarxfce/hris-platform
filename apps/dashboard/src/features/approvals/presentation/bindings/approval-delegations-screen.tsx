import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadApprovalInbox } from "../../domain/policies/approval-read-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalDelegationsController } from "../controllers/approval-delegations-controller";
import { approvalDelegationsView } from "../models/approval-delegation-view";
import { approvalParameters } from "../models/approval-route";
import { ApprovalDelegationsPage } from "../pages/approval-delegations-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalDelegationsScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after =
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null;
  const canonical = approvalParameters(props.access.companyId, after);
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals/delegations?${canonical}`} replace />;
  return (
    <ApprovalDelegationsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      onPage={(cursor) => setParameters(approvalParameters(props.access.companyId, cursor))}
      onOpen={(id) => navigate(`/approvals/delegations/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function ApprovalDelegationsBinding({
  accountId,
  access,
  approvals,
  companyName,
  timezone,
  locale,
  after,
  onPage,
  onOpen,
}: Props & {
  after: string | null;
  onPage: (cursor: string | null) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new ApprovalDelegationsController(approvals.loadDelegations, access, accountId, after),
    [approvals.loadDelegations, access, accountId, after],
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
    () => (state.page ? approvalDelegationsView(state.page, accountId, locale, timezone) : []),
    [state.page, accountId, locale, timezone],
  );
  return (
    <ApprovalDelegationsPage
      state={state}
      rows={rows}
      companyName={companyName}
      timezone={timezone}
      createTo={
        canReadApprovalInbox(access.permissions)
          ? `/approvals/delegations/new?${approvalParameters(access.companyId, after)}`
          : null
      }
      locale={locale}
      firstPage={after === null}
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={onOpen}
    />
  );
}
