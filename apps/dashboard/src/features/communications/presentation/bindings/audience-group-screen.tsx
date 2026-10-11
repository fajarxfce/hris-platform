import { useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { useAudienceGroup } from "../controllers/use-audience-group";
import { announcementParameters } from "../models/announcement-route";
import { audienceGroupsPath, audienceGroupView } from "../models/audience-group-view";
import { AudienceGroupPage } from "../pages/audience-group-page";
import { AudienceSelection } from "./audience-selection";

export function AudienceGroupScreen(props: CommunicationsScreenProps) {
  const { groupId = "" } = useParams();
  const [parameters] = useSearchParams();
  const after = parameters.get("after");
  const revision = parameters.get("revision");
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${audienceGroupsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company"))
    return (
      <Navigate
        to={`${audienceGroupsPath}/${encodeURIComponent(groupId)}?${announcementParameters(props.access.companyId, after, revision)}`}
        replace
      />
    );
  return (
    <AudienceGroupBinding
      key={`${props.accountId}:${props.access.companyId}:${groupId}`}
      {...props}
      id={groupId}
      after={after}
      revision={revision}
    />
  );
}
function AudienceGroupBinding(
  props: CommunicationsScreenProps & { id: string; after: string | null; revision: string | null },
) {
  const state = useAudienceGroup(
    props.accountId,
    props.access,
    props.id,
    props.revision,
    props.communications.loadGroup,
  );
  useWorkspaceRevalidation(state.failure);
  const properties = useMemo(
    () => (state.group ? audienceGroupView(state.group, props.locale, props.timezone) : []),
    [state.group, props.locale, props.timezone],
  );
  const query = announcementParameters(props.access.companyId, props.after);
  const path = `${audienceGroupsPath}/${encodeURIComponent(props.id)}`;
  return (
    <AudienceGroupPage
      state={state}
      properties={properties}
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${audienceGroupsPath}?${query}`}
      editTo={
        state.group && props.revision === null && state.group.version < 999
          ? `${path}/edit?${query}`
          : null
      }
      previousTo={
        state.group && state.group.version > 0
          ? `${path}?${announcementParameters(props.access.companyId, props.after, String(state.group.version - 1))}`
          : null
      }
      currentTo={props.revision !== null ? `${path}?${query}` : null}
      members={
        state.group ? (
          <AudienceSelection
            {...props}
            kind="EMPLOYMENT"
            ids={state.group.employmentIds}
            maximum={5000}
            readOnly
          />
        ) : null
      }
    />
  );
}
