import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { CompanyMembershipScreenProps } from "../contracts/company-membership-screen-props";
import { CompanyMembershipCreationController } from "../controllers/company-membership-creation-controller";
import { useCompanyMembershipCreationForm } from "../controllers/use-company-membership-creation-form";
import { usePlatformAccountSession } from "../controllers/use-platform-account-session";
import { companyAccountContext } from "../models/company-account-route";
import { CompanyMembershipCreationPage } from "../pages/company-membership-creation-page";
import { CompanyRolePicker } from "./company-role-picker";

export function CompanyMembershipCreationScreen(props: CompanyMembershipScreenProps) {
  const { memberId = "" } = useParams();
  const [parameters] = useSearchParams();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />;
  return (
    <CompanyMembershipCreationBinding
      key={`${props.accountId}:${props.access.companyId}:${memberId}`}
      {...props}
      memberId={memberId}
      context={companyAccountContext(props.access.companyId, parameters)}
    />
  );
}
function CompanyMembershipCreationBinding(
  props: CompanyMembershipScreenProps & { memberId: string; context: string },
) {
  const access = useMemo(
    () => Object.freeze({ company: props.access, platform: props.platform }),
    [props.access, props.platform],
  );
  const controller = useMemo(
    () =>
      new CompanyMembershipCreationController(
        props.actions,
        access,
        props.memberId,
        props.nextIdentifier,
      ),
    [props.actions, access, props.memberId, props.nextIdentifier],
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
  usePlatformAccountSession(state.failure, props.platform);
  const form = useCompanyMembershipCreationForm(
    controller,
    state,
    props.access.companyId,
    props.locale,
  );
  return (
    <CompanyMembershipCreationPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/administration/members/new?${props.context}`}
      memberTo={`/administration/members/${encodeURIComponent(props.memberId)}?company=${props.access.companyId}`}
      onRetry={controller.retrySave}
      picker={
        form.picker && (
          <CompanyRolePicker
            accountId={props.accountId}
            access={props.access}
            load={props.actions.loadRoleTemplates}
            excludedRoleIds={form.excludedRoleIds}
            locale={props.locale}
            onSelect={form.selectRole}
            onClose={form.closePicker}
          />
        )
      }
    />
  );
}
