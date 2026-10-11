import type { Announcement } from "./announcement";

export type AnnouncementAction =
  | "EDIT"
  | "PREVIEW"
  | "PUBLISH"
  | "RETURN_TO_DRAFT"
  | "ARCHIVE"
  | "VIEW_JOB";
export type AnnouncementPublicationJob = Readonly<{
  id: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  cancellationRequested: boolean;
  version: number;
  failureCode: string | null;
}>;
export type AnnouncementReview = Readonly<{
  announcement: Announcement;
  publicationJob: AnnouncementPublicationJob | null;
  availableActions: readonly AnnouncementAction[];
  evaluatedAt: string;
}>;
