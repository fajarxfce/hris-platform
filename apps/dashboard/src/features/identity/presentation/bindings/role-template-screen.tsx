import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { RoleTemplateScreenProps } from "../contracts/role-template-screen-props";
import { RoleTemplateController } from "../controllers/role-template-controller";
import { roleTemplateParameters } from "../models/role-template-route";
import { roleTemplateRevisionView, roleTemplateView } from "../models/role-template-view";
import { RoleTemplatePage } from "../pages/role-template-page";
export function RoleTemplateScreen(props: RoleTemplateScreenProps) {
  const { roleId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const directory = roleTemplateParameters(props.access.companyId, parameters);
  const backTo = `/administration/roles?${directory}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={backTo} replace />;
  const historyAfter = parameters.get("historyAfter");
  if (!parameters.has("company")) {
    if (historyAfter !== null) directory.set("historyAfter", historyAfter);
    return (
      <Navigate to={`/administration/roles/${encodeURIComponent(roleId)}?${directory}`} replace />
    );
  }
  return (
    <RoleTemplateBinding
      key={`${props.accountId}:${props.access.companyId}:${roleId}`}
      {...props}
      id={roleId}
      historyAfter={historyAfter}
      backTo={backTo}
      editTo={`/administration/roles/${encodeURIComponent(roleId)}/edit?${directory}`}
      onHistory={(after) => {
        const next = new URLSearchParams(directory);
        if (after !== null) next.set("historyAfter", after);
        setParameters(next);
      }}
    />
  );
}
function RoleTemplateBinding(
  props: RoleTemplateScreenProps & {
    id: string;
    historyAfter: string | null;
    backTo: string;
    editTo: string;
    onHistory: (after: string | null) => void;
  },
) {
  const controller = useMemo(
    () =>
      new RoleTemplateController(
        props.actions.loadRoleTemplate,
        props.access,
        props.id,
        props.historyAfter,
      ),
    [props.actions.loadRoleTemplate, props.access, props.id, props.historyAfter],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  const revisionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  useEffect(() => {
    if (state.selectedRevision) revisionRef.current?.focus();
  }, [state.selectedRevision]);
  useWorkspaceRevalidation(state.failure);
  const view = useMemo(
    () => (state.review ? roleTemplateView(state.review, props.locale, props.timezone) : null),
    [state.review, props.locale, props.timezone],
  );
  const selected = useMemo(
    () =>
      state.selectedRevision
        ? roleTemplateRevisionView(state.selectedRevision, props.locale, props.timezone)
        : null,
    [state.selectedRevision, props.locale, props.timezone],
  );
  return (
    <RoleTemplatePage
      state={state}
      view={view}
      selected={selected}
      revisionRef={revisionRef}
      companyName={props.companyName}
      locale={props.locale}
      timezone={props.timezone}
      backTo={props.backTo}
      editTo={state.review ? props.editTo : null}
      firstHistory={props.historyAfter === null}
      onRefresh={controller.refresh}
      onRevision={controller.selectRevision}
      onLatest={() => props.onHistory(null)}
      onOlder={() => {
        if (state.review?.history.nextCursor) props.onHistory(state.review.history.nextCursor);
      }}
    />
  );
}
