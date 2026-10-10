import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { CompanyMemberController } from "../controllers/company-member-controller";
import { companyMemberProperties } from "../models/company-member-view";
import { CompanyMemberPage } from "../pages/company-member-page";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: IdentityAdministrationUseCases;
  companyName: string;
  locale: Locale;
};
export function CompanyMemberScreen(props: Props) {
  const { memberId = "" } = useParams();
  const [parameters] = useSearchParams();
  const company = parameters.get("company");
  if (company !== null && company !== props.access.companyId)
    return <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />;
  if (company === null)
    return (
      <Navigate
        to={`/administration/members/${encodeURIComponent(memberId)}?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <CompanyMemberBinding
      key={`${props.accountId}:${props.access.companyId}:${memberId}`}
      {...props}
      memberId={memberId}
    />
  );
}
function CompanyMemberBinding(props: Props & { memberId: string }) {
  const controller = useMemo(
    () =>
      new CompanyMemberController(props.actions.loadCompanyMember, props.access, props.memberId),
    [props.actions, props.access, props.memberId],
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
  const properties = useMemo(
    () => (state.grant ? companyMemberProperties(state.grant, props.locale) : []),
    [state.grant, props.locale],
  );
  return (
    <CompanyMemberPage
      state={state}
      properties={properties}
      locale={props.locale}
      companyName={props.companyName}
      onRefresh={controller.refresh}
      backTo={`/administration/members?company=${props.access.companyId}`}
    />
  );
}
