import type { AnnouncementAudiencePreviewDto } from "../data/models/announcement-audience-preview-dto";
import type { AnnouncementReviewDto } from "../data/models/announcement-review-dto";
import { announcementId, detail } from "./communications-fixture";

export const publicationJobId = "82000000-0000-4000-8000-000000000001";
export const reviewDetail = (version = 0): AnnouncementReviewDto => ({
  announcement: detail(version),
  publicationJob: null,
  availableActions: ["EDIT", "PREVIEW", "PUBLISH", "ARCHIVE"],
  evaluatedAt: "2026-10-11T01:00:00Z",
});
export const previewDetail = (version = 0): AnnouncementAudiencePreviewDto => ({
  announcementId,
  version,
  asOfDate: "2026-10-11",
  evaluatedAt: "2026-10-11T01:00:00Z",
  recipientCount: 3,
  audienceVersions: {},
});
