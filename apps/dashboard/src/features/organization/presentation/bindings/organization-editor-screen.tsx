import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import { OrganizationEditorController } from "../controllers/organization-editor-controller";
import { useOrganizationEditorForm } from "../controllers/use-organization-editor-form";
import {
  organizationSearchFromParameters,
  organizationSearchParameters,
} from "../models/organization-route";
import { OrganizationEditorPage } from "../pages/organization-editor-page";
import { OrganizationParentPicker } from "./organization-parent-picker";

type Props = {
  accountId: AccountId;
  access: CompanyAccess;
  organization: OrganizationUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  creating: boolean;
  nextIdentifier: () => string;
};
export function OrganizationEditorScreen(props: Props) {
  const { unitId = "" } = useParams();
  const [parameters] = useSearchParams();
  const search = organizationSearchFromParameters(parameters, props.access.companyId);
  const pinned = organizationSearchParameters(search, props.access.companyId).toString();
  const backTo = `/organization/units?${pinned}`;
  const path = props.creating
    ? "/organization/units/new"
    : `/organization/units/${encodeURIComponent(unitId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <OrganizationEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}`}
      {...props}
      unitId={unitId}
      backTo={backTo}
      parameters={pinned}
    />
  );
}

function OrganizationEditorBinding(
  props: Props & { unitId: string; backTo: string; parameters: string },
) {
  const { creating, nextIdentifier, unitId, organization, access } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : unitId),
    [creating, nextIdentifier, unitId],
  );
  const controller = useMemo(
    () => new OrganizationEditorController(organization, access, creating, id, nextIdentifier),
    [organization, access, creating, id, nextIdentifier],
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
  const form = useOrganizationEditorForm(controller, state, props.timezone);
  useWorkspaceRevalidation(state.failure);
  return (
    <>
      <OrganizationEditorPage
        state={state}
        form={form}
        creating={creating}
        companyName={props.companyName}
        locale={props.locale}
        backTo={props.backTo}
        detailTo={`/organization/units/${id}?${props.parameters}`}
        onRetrySave={controller.retrySave}
      />
      {form.choosingParent && (
        <OrganizationParentPicker
          loadUnits={organization.loadUnits}
          access={access}
          kinds={form.parentKinds}
          excludedId={id}
          onSelect={form.selectParent}
          onClose={() => form.chooseParent(false)}
          locale={props.locale}
        />
      )}
    </>
  );
}
