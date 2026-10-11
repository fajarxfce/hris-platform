import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { AnnouncementCommandKind } from "../../domain/entities/announcement-command";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AnnouncementCommandController } from "../controllers/announcement-command-controller";
import { useAnnouncementCommandForm } from "../controllers/use-announcement-command-form";
import { announcementPreviewView } from "../models/announcement-publication-view";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { AnnouncementCommandPage } from "../pages/announcement-command-page";
import { AudienceSelection } from "./audience-selection";

export function AnnouncementCommandScreen(
  props: CommunicationsScreenProps & { kind: AnnouncementCommandKind },
) {
  const { announcementId = "" } = useParams();
  const [parameters] = useSearchParams();
  const action =
    props.kind === "PUBLISH" ? "publish" : props.kind === "ARCHIVE" ? "archive" : "return-to-draft";
  const path = `${announcementsPath}/${encodeURIComponent(announcementId)}/${action}`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company"))
    return <Navigate to={`${path}?${announcementParameters(props.access.companyId)}`} replace />;
  return (
    <AnnouncementCommandBinding
      key={`${props.accountId}:${props.access.companyId}:${path}`}
      {...props}
      id={announcementId}
    />
  );
}
function AnnouncementCommandBinding(
  props: CommunicationsScreenProps & { kind: AnnouncementCommandKind; id: string },
) {
  const controller = useMemo(
    () =>
      new AnnouncementCommandController(
        props.communications,
        props.access,
        props.id,
        props.kind,
        props.nextIdentifier,
      ),
    [props.communications, props.access, props.id, props.kind, props.nextIdentifier],
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
  const form = useAnnouncementCommandForm(controller, state, props.timezone);
  useWorkspaceRevalidation(state.failure);
  const previewProperties = useMemo(
    () =>
      state.preview ? announcementPreviewView(state.preview, props.locale, props.timezone) : [],
    [state.preview, props.locale, props.timezone],
  );
  const query = announcementParameters(props.access.companyId);
  const reviewPath = `${announcementsPath}/${encodeURIComponent(props.id)}/publication?${query}`;
  const announcement = state.review?.announcement;
  return (
    <AnnouncementCommandPage
      state={state}
      form={form}
      kind={props.kind}
      locale={props.locale}
      companyName={props.companyName}
      timezone={props.timezone}
      previewProperties={previewProperties}
      backTo={reviewPath}
      savedTo={state.receipt ? reviewPath : null}
      onRetry={controller.retry}
      onConfirm={controller.confirm}
      onDismiss={controller.dismiss}
      audience={
        announcement && announcement.audienceKind !== "COMPANY" ? (
          <AudienceSelection
            {...props}
            kind={announcement.audienceKind}
            ids={announcement.targetIds}
            maximum={32}
            readOnly
          />
        ) : null
      }
    />
  );
}
