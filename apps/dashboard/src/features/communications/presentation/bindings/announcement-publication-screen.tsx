import { useMemo } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { AnnouncementCommandKind } from "../../domain/entities/announcement-command";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { useAnnouncementPreview } from "../controllers/use-announcement-preview";
import { useAnnouncementReview } from "../controllers/use-announcement-review";
import {
  announcementJobView,
  announcementPreviewView,
} from "../models/announcement-publication-view";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { announcementView } from "../models/announcement-view";
import { AnnouncementPublicationPage } from "../pages/announcement-publication-page";

export function AnnouncementPublicationScreen(props: CommunicationsScreenProps) {
  const { announcementId = "" } = useParams();
  const [parameters] = useSearchParams();
  const path = `${announcementsPath}/${encodeURIComponent(announcementId)}/publication`;
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
    <AnnouncementPublicationBinding
      key={`${props.accountId}:${props.access.companyId}:${announcementId}`}
      {...props}
      id={announcementId}
    />
  );
}
function AnnouncementPublicationBinding(props: CommunicationsScreenProps & { id: string }) {
  const state = useAnnouncementReview(
    props.accountId,
    props.access,
    props.id,
    props.communications.loadReview,
  );
  const preview = useAnnouncementPreview(
    props.accountId,
    props.access,
    state.review,
    props.communications.previewAudience,
  );
  useWorkspaceRevalidation(state.failure ?? preview.failure);
  const properties = useMemo(
    () =>
      state.review ? announcementView(state.review.announcement, props.locale, props.timezone) : [],
    [state.review, props.locale, props.timezone],
  );
  const previewProperties = useMemo(
    () =>
      preview.preview ? announcementPreviewView(preview.preview, props.locale, props.timezone) : [],
    [preview.preview, props.locale, props.timezone],
  );
  const jobProperties = useMemo(
    () => (state.review ? announcementJobView(state.review, props.locale) : []),
    [state.review, props.locale],
  );
  const path = `${announcementsPath}/${encodeURIComponent(props.id)}`;
  const query = announcementParameters(props.access.companyId);
  const actions: readonly { action: AnnouncementCommandKind; to: string }[] = (
    ["PUBLISH", "RETURN_TO_DRAFT", "ARCHIVE"] as const
  )
    .filter((action) => state.review?.availableActions.includes(action))
    .map((action) => ({
      action,
      to: `${path}/${action === "PUBLISH" ? "publish" : action === "ARCHIVE" ? "archive" : "return-to-draft"}?${query}`,
    }));
  return (
    <AnnouncementPublicationPage
      state={state}
      preview={preview}
      properties={properties}
      previewProperties={previewProperties}
      jobProperties={jobProperties}
      jobFailure={
        state.review?.publicationJob?.failureCode
          ? { code: state.review.publicationJob.failureCode, fields: {}, parameters: {} }
          : null
      }
      active={
        state.review?.publicationJob?.status === "QUEUED" ||
        state.review?.publicationJob?.status === "RUNNING"
      }
      locale={props.locale}
      companyName={props.companyName}
      backTo={`${path}?${query}`}
      actions={actions}
      jobTo={
        state.review?.availableActions.includes("VIEW_JOB") && state.review.publicationJob
          ? `/administration/jobs?${query}&job=${state.review.publicationJob.id}`
          : null
      }
    />
  );
}
