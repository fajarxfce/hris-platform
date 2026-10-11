import { useMemo } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { useAudienceGroups } from "../controllers/use-audience-groups";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { audienceGroupRows, audienceGroupsPath } from "../models/audience-group-view";
import { AudienceGroupsPage } from "../pages/audience-groups-page";

export function AudienceGroupsScreen(props: CommunicationsScreenProps) {
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const after = parameters.get("after");
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
        to={`${audienceGroupsPath}?${announcementParameters(props.access.companyId, after)}`}
        replace
      />
    );
  return (
    <AudienceGroupsBinding
      key={`${props.accountId}:${props.access.companyId}`}
      {...props}
      after={after}
      onPage={(value) => setParameters(announcementParameters(props.access.companyId, value))}
      onOpen={(id) =>
        navigate(
          `${audienceGroupsPath}/${id}?${announcementParameters(props.access.companyId, after)}`,
        )
      }
    />
  );
}
function AudienceGroupsBinding(
  props: CommunicationsScreenProps & {
    after: string | null;
    onPage: (value: string | null) => void;
    onOpen: (id: string) => void;
  },
) {
  const state = useAudienceGroups(
    props.accountId,
    props.access,
    props.after,
    props.communications.loadGroups,
  );
  useWorkspaceRevalidation(state.failure);
  const rows = useMemo(
    () => (state.page ? audienceGroupRows(state.page, props.locale, props.timezone) : []),
    [state.page, props.locale, props.timezone],
  );
  const query = announcementParameters(props.access.companyId, props.after);
  return (
    <AudienceGroupsPage
      state={state}
      rows={rows}
      locale={props.locale}
      companyName={props.companyName}
      createTo={`${audienceGroupsPath}/new?${query}`}
      announcementsTo={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
      firstPage={props.after === null}
      onFirst={() => props.onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) props.onPage(state.page.nextCursor);
      }}
      onOpen={props.onOpen}
    />
  );
}
