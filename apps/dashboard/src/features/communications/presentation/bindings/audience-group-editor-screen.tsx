import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AudienceGroupEditorController } from "../controllers/audience-group-editor-controller";
import { useAudienceGroupForm } from "../controllers/use-audience-group-form";
import { announcementParameters } from "../models/announcement-route";
import { audienceGroupsPath } from "../models/audience-group-view";
import { AudienceGroupEditorPage } from "../pages/audience-group-editor-page";
import { AudienceSelection } from "./audience-selection";

export function AudienceGroupEditorScreen(
  props: CommunicationsScreenProps & { creating: boolean },
) {
  const { groupId = "" } = useParams();
  const [parameters] = useSearchParams();
  const query = announcementParameters(props.access.companyId, parameters.get("after")).toString();
  const route = props.creating
    ? `${audienceGroupsPath}/new`
    : `${audienceGroupsPath}/${encodeURIComponent(groupId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${audienceGroupsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company")) return <Navigate to={`${route}?${query}`} replace />;
  return (
    <AudienceGroupEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${route}`}
      {...props}
      id={groupId}
      parameters={query}
    />
  );
}
function AudienceGroupEditorBinding(
  props: CommunicationsScreenProps & { creating: boolean; id: string; parameters: string },
) {
  const id = useMemo(
    () => (props.creating ? props.nextIdentifier() : props.id),
    [props.creating, props.id, props.nextIdentifier],
  );
  const controller = useMemo(
    () =>
      new AudienceGroupEditorController(
        props.communications,
        props.access,
        props.creating,
        id,
        props.nextIdentifier,
      ),
    [props.communications, props.access, props.creating, id, props.nextIdentifier],
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
  const form = useAudienceGroupForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  return (
    <AudienceGroupEditorPage
      state={state}
      form={form}
      creating={props.creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`${audienceGroupsPath}?${props.parameters}`}
      savedTo={
        state.receipt ? `${audienceGroupsPath}/${state.receipt.id}?${props.parameters}` : null
      }
      onRetry={controller.retrySave}
      members={
        <AudienceSelection
          {...props}
          kind="EMPLOYMENT"
          ids={form.employmentIds.value}
          maximum={5000}
          enabled={form.editable}
          onChange={form.employmentIds.onChange}
        />
      }
    />
  );
}
