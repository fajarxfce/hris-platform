import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyMembershipScreenProps } from "../contracts/company-membership-screen-props";
import { CompanyMembershipEditorController } from "../controllers/company-membership-editor-controller";
import { useCompanyMembershipForm } from "../controllers/use-company-membership-form";
import { CompanyMembershipEditorPage } from "../pages/company-membership-editor-page";
import { CompanyRolePicker } from "./company-role-picker";

export function CompanyMembershipEditorScreen(props: CompanyMembershipScreenProps) {
  const { memberId = "" } = useParams();
  const [parameters] = useSearchParams();
  const company = parameters.get("company");
  if (company !== null && company !== props.access.companyId)
    return <Navigate to={`/administration/members?company=${props.access.companyId}`} replace />;
  if (company === null)
    return (
      <Navigate
        to={`/administration/members/${encodeURIComponent(memberId)}/edit?company=${props.access.companyId}`}
        replace
      />
    );
  return (
    <CompanyMembershipEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${memberId}`}
      {...props}
      memberId={memberId}
    />
  );
}

function CompanyMembershipEditorBinding(
  props: CompanyMembershipScreenProps & { memberId: string },
) {
  const { actions, access, memberId, nextIdentifier } = props;
  const controller = useMemo(
    () => new CompanyMembershipEditorController(actions, access, memberId, nextIdentifier),
    [actions, access, memberId, nextIdentifier],
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
  const form = useCompanyMembershipForm(controller, state, access.companyId, props.locale);
  return (
    <CompanyMembershipEditorPage
      state={state}
      form={form}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/administration/members/${encodeURIComponent(memberId)}?company=${access.companyId}`}
      onRetry={controller.retrySave}
      picker={
        form.picker && (
          <CompanyRolePicker
            accountId={props.accountId}
            access={access}
            load={actions.loadRoleTemplates}
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
