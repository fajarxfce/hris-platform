import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { RoleTemplateScreenProps } from "../contracts/role-template-screen-props";
import { RoleTemplateEditorController } from "../controllers/role-template-editor-controller";
import { useRoleTemplateForm } from "../controllers/use-role-template-form";
import { roleTemplateParameters } from "../models/role-template-route";
import { RoleTemplateEditorPage } from "../pages/role-template-editor-page";

type Props = RoleTemplateScreenProps & { creating: boolean };
export function RoleTemplateEditorScreen(props: Props) {
  const { roleId = "" } = useParams();
  const [parameters] = useSearchParams();
  const directory = roleTemplateParameters(props.access.companyId, parameters).toString();
  const path = props.creating
    ? "/administration/roles/new"
    : `/administration/roles/${encodeURIComponent(roleId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/administration/roles?${directory}`} replace />;
  if (!parameters.has("company")) return <Navigate to={`${path}?${directory}`} replace />;
  return (
    <RoleTemplateEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${path}:${directory}`}
      {...props}
      roleId={roleId}
      parameters={directory}
    />
  );
}
function RoleTemplateEditorBinding(props: Props & { roleId: string; parameters: string }) {
  const { actions, access, creating, nextIdentifier, roleId } = props;
  const id = useMemo(
    () => (creating ? nextIdentifier() : roleId),
    [creating, nextIdentifier, roleId],
  );
  const controller = useMemo(
    () => new RoleTemplateEditorController(actions, access, creating, id, nextIdentifier),
    [actions, access, creating, id, nextIdentifier],
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
  const form = useRoleTemplateForm(controller, state, props.locale);
  return (
    <RoleTemplateEditorPage
      state={state}
      form={form}
      creating={creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`/administration/roles?${props.parameters}`}
      savedTo={
        state.receipt ? `/administration/roles/${state.receipt.id}?${props.parameters}` : null
      }
      onRetry={controller.retrySave}
    />
  );
}
