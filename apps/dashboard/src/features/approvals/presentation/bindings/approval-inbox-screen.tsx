import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import { ApprovalInboxController } from "../controllers/approval-inbox-controller";
import { approvalParameters } from "../models/approval-route";
import { approvalInboxView } from "../models/approval-view";
import { ApprovalInboxPage } from "../pages/approval-inbox-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  approvals: ApprovalsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
};
export function ApprovalInboxScreen(props: Props) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after =
    parameters.get("company") === props.access.companyId ? parameters.get("after") : null;
  const canonical = approvalParameters(props.access.companyId, after);
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/approvals?${canonical}`} replace />;
  return (
    <ApprovalInboxBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      onPage={(cursor) => setParameters(approvalParameters(props.access.companyId, cursor))}
      onOpen={(id) => navigate(`/approvals/${encodeURIComponent(id)}?${canonical}`)}
    />
  );
}
function ApprovalInboxBinding({
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
    () => new ApprovalInboxController(approvals.loadInbox, access, after),
    [approvals.loadInbox, access, after],
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
    () => (state.page ? approvalInboxView(state.page, locale, timezone) : []),
    [state.page, locale, timezone],
  );
  return (
    <ApprovalInboxPage
      state={state}
      rows={rows}
      companyName={companyName}
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
