import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { CompanyMembersController } from "../controllers/company-members-controller";
import { companyMemberRows } from "../models/company-member-view";
import { CompanyMembersPage } from "../pages/company-members-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: IdentityAdministrationUseCases;
  companyName: string;
  locale: Locale;
};
export function CompanyMembersScreen(props: Props) {
  const [parameters] = useSearchParams();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />;
  const after = parameters.get("after");
  return (
    <CompanyMembersBinding
      key={`${props.accountId}:${props.access.companyId}:${after}`}
      {...props}
      after={after}
    />
  );
}
function CompanyMembersBinding(props: Props & { after: string | null }) {
  const navigate = useNavigate();
  const [, setParameters] = useSearchParams();
  const controller = useMemo(
    () => new CompanyMembersController(props.actions.loadCompanyMembers, props.access, props.after),
    [props.actions, props.access, props.after],
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
    () => companyMemberRows(state.page?.items ?? [], props.locale),
    [state.page, props.locale],
  );
  return (
    <CompanyMembersPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      firstPage={props.after === null}
      onRefresh={controller.refresh}
      onFirst={() => setParameters({ company: props.access.companyId })}
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({ company: props.access.companyId, after: state.page.nextCursor });
      }}
      onOpen={(id) =>
        navigate(
          `/administration/members/${encodeURIComponent(id)}?company=${props.access.companyId}`,
        )
      }
    />
  );
}
