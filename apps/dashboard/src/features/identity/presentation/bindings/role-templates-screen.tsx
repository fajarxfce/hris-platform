import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import { canManageRoleTemplates } from "../../domain/policies/role-template-policy";
import type { RoleTemplateScreenProps } from "../contracts/role-template-screen-props";
import { RoleTemplatesController } from "../controllers/role-templates-controller";
import { roleTemplateParameters } from "../models/role-template-route";
import { roleTemplateRows } from "../models/role-template-view";
import { RoleTemplatesPage } from "../pages/role-templates-page";
export function RoleTemplatesScreen(props: RoleTemplateScreenProps) {
  const [parameters] = useSearchParams();
  const canonical = roleTemplateParameters(props.access.companyId, parameters).toString();
  if (parameters.get("company") !== props.access.companyId)
    return <Navigate to={`/administration/roles?${canonical}`} replace />;
  return (
    <RoleTemplatesBinding
      key={`${props.accountId}:${props.access.companyId}:${parameters.get("after")}`}
      {...props}
      after={parameters.get("after")}
      parameters={canonical}
    />
  );
}
function RoleTemplatesBinding(
  props: RoleTemplateScreenProps & { after: string | null; parameters: string },
) {
  const navigate = useNavigate();
  const [, setParameters] = useSearchParams();
  const controller = useMemo(
    () => new RoleTemplatesController(props.actions.loadRoleTemplates, props.access, props.after),
    [props.actions.loadRoleTemplates, props.access, props.after],
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
    () => roleTemplateRows(state.page, props.locale),
    [state.page, props.locale],
  );
  return (
    <RoleTemplatesPage
      state={state}
      rows={rows}
      companyName={props.companyName}
      locale={props.locale}
      firstPage={props.after === null}
      createTo={
        canManageRoleTemplates(props.access.permissions)
          ? `/administration/roles/new?${props.parameters}`
          : null
      }
      onRefresh={controller.refresh}
      onFirst={() => setParameters({ company: props.access.companyId })}
      onNext={() => {
        if (state.page?.nextCursor)
          setParameters({ company: props.access.companyId, after: state.page.nextCursor });
      }}
      onOpen={(id) =>
        navigate(`/administration/roles/${encodeURIComponent(id)}?${props.parameters}`)
      }
    />
  );
}
