import { useContext, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { WorkspaceVisibilityContext } from "../../../../core/presentation/contracts/workspace-visibility";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AnnouncementDeliveryController } from "../controllers/announcement-delivery-controller";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { AnnouncementDeliveryPage } from "../pages/announcement-delivery-page";

export function AnnouncementDeliveryScreen(props: CommunicationsScreenProps) {
  const { announcementId = "" } = useParams();
  const [parameters] = useSearchParams();

  const pinned = announcementParameters(props.access.companyId, parameters.get("after"));
  const resource = `${announcementsPath}/${encodeURIComponent(announcementId)}/delivery`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company")) return <Navigate to={`${resource}?${pinned}`} replace />;
  return (
    <AnnouncementDeliveryBinding
      key={`${props.accountId}:${props.access.companyId}:${announcementId}`}
      {...props}
      id={announcementId}
      after={parameters.get("after")}
    />
  );
}
function AnnouncementDeliveryBinding(
  props: CommunicationsScreenProps & { id: string; after: string | null },
) {
  const visible = useContext(WorkspaceVisibilityContext);
  const [foreground, setForeground] = useState(() => document.visibilityState !== "hidden");
  const controller = useMemo(
    () =>
      new AnnouncementDeliveryController(props.communications.loadDelivery, props.access, props.id),
    [props.communications.loadDelivery, props.access, props.id],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    // Batch visibility with workspace admission before starting another read.
    // Global identity recovery also observes foreground; it must settle first.
    const changed = () => setForeground(document.visibilityState !== "hidden");
    changed();
    document.addEventListener("visibilitychange", changed);
    return () => document.removeEventListener("visibilitychange", changed);
  }, []);
  useEffect(() => {
    if (visible && foreground) controller.activate();
    else controller.deactivate();
    return controller.deactivate;
  }, [controller, visible, foreground]);
  useWorkspaceRevalidation(state.failure);
  return (
    <AnnouncementDeliveryPage
      state={state}
      companyName={props.companyName}
      locale={props.locale}
      timezone={props.timezone}
      backTo={`${announcementsPath}/${encodeURIComponent(props.id)}?${announcementParameters(props.access.companyId, props.after)}`}
      onRefresh={controller.refresh}
    />
  );
}
