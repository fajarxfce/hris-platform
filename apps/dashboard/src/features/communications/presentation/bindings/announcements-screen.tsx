import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AnnouncementListController } from "../controllers/announcement-list-controller";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { announcementRows } from "../models/announcement-view";
import { AnnouncementsPage } from "../pages/announcements-page";

export function AnnouncementsScreen(props: CommunicationsScreenProps & { history?: boolean }) {
  const { announcementId = "" } = useParams();
  const [parameters, setParameters] = useSearchParams();
  const navigate = useNavigate();
  const historyId = props.history ? announcementId : null;
  const path =
    historyId === null
      ? announcementsPath
      : `${announcementsPath}/${encodeURIComponent(historyId)}/history`;
  const after = parameters.get("after");
  const pinned = announcementParameters(props.access.companyId, after);
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company")) return <Navigate to={`${path}?${pinned}`} replace />;
  return (
    <AnnouncementListBinding
      key={`${props.accountId}:${props.access.companyId}:${historyId}`}
      {...props}
      historyId={historyId}
      after={after}
      onPage={(value) => setParameters(announcementParameters(props.access.companyId, value))}
      onOpen={(value) =>
        navigate(
          historyId === null
            ? `${announcementsPath}/${encodeURIComponent(value)}?${pinned}`
            : announcementsPath +
                "/" +
                encodeURIComponent(historyId) +
                "?" +
                announcementParameters(props.access.companyId, null, value),
        )
      }
    />
  );
}
function AnnouncementListBinding({
  communications,
  access,
  historyId,
  after,
  onPage,
  onOpen,
  ...props
}: CommunicationsScreenProps & {
  historyId: string | null;
  after: string | null;
  onPage: (after: string | null) => void;
  onOpen: (id: string) => void;
}) {
  const controller = useMemo(
    () => new AnnouncementListController(communications, access, historyId, after),
    [communications, access, historyId, after],
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
    () =>
      state.page
        ? announcementRows(state.page, historyId !== null, props.locale, props.timezone)
        : [],
    [state.page, historyId, props.locale, props.timezone],
  );
  return (
    <AnnouncementsPage
      state={state}
      rows={rows}
      history={historyId !== null}
      createTo={
        historyId === null
          ? `${announcementsPath}/new?${announcementParameters(access.companyId)}`
          : null
      }
      companyName={props.companyName}
      locale={props.locale}
      firstPage={after === null}
      backTo={
        historyId === null
          ? null
          : announcementsPath +
            "/" +
            encodeURIComponent(historyId) +
            "?" +
            announcementParameters(access.companyId)
      }
      onRefresh={controller.refresh}
      onFirst={() => onPage(null)}
      onNext={() => {
        if (state.page?.nextCursor) onPage(state.page.nextCursor);
      }}
      onOpen={onOpen}
    />
  );
}
