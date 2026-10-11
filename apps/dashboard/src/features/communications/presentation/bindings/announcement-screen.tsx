import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AnnouncementController } from "../controllers/announcement-controller";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { announcementView } from "../models/announcement-view";
import { AnnouncementPage } from "../pages/announcement-page";

export function AnnouncementScreen(props: CommunicationsScreenProps) {
  const { announcementId = "" } = useParams();
  const [parameters] = useSearchParams();
  const revision = parameters.get("revision");
  const pinned = announcementParameters(props.access.companyId, parameters.get("after"), revision);
  const resource = `${announcementsPath}/${encodeURIComponent(announcementId)}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company")) return <Navigate to={`${resource}?${pinned}`} replace />;
  return (
    <AnnouncementBinding
      key={`${props.accountId}:${props.access.companyId}:${announcementId}:${revision}`}
      {...props}
      id={announcementId}
      revision={revision}
      after={parameters.get("after")}
    />
  );
}
function AnnouncementBinding({
  communications,
  access,
  id,
  revision,
  after,
  ...props
}: CommunicationsScreenProps & {
  id: string;
  revision: string | null;
  after: string | null;
}) {
  const controller = useMemo(
    () => new AnnouncementController(communications.loadAnnouncement, access, id, revision),
    [communications.loadAnnouncement, access, id, revision],
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
    () =>
      state.announcement
        ? announcementView(state.announcement, props.locale, props.timezone)
        : null,
    [state.announcement, props.locale, props.timezone],
  );
  const pinned = announcementParameters(access.companyId).toString();
  const resource = `${announcementsPath}/${encodeURIComponent(id)}`;
  return (
    <AnnouncementPage
      state={state}
      properties={properties}
      companyName={props.companyName}
      timezone={props.timezone}
      locale={props.locale}
      backTo={`${announcementsPath}?${announcementParameters(access.companyId, after)}`}
      historyTo={`${resource}/history?${pinned}`}
      currentTo={revision === null ? null : `${resource}?${pinned}`}
      publicationTo={
        revision === null && state.announcement ? `${resource}/publication?${pinned}` : null
      }
      editTo={
        revision === null && state.announcement?.status === "DRAFT"
          ? `${resource}/edit?${pinned}`
          : null
      }
      jobTo={
        state.announcement?.publicationJobId
          ? `/administration/jobs?${pinned}&job=${state.announcement.publicationJobId}`
          : null
      }
      onRefresh={controller.refresh}
    />
  );
}
