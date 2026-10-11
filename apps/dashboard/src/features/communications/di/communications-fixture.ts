import type { CompanyId } from "../../../core/domain/identifiers";
import type { AnnouncementDto, AnnouncementSummaryDto } from "../data/models/announcement-dto";

export const companyId = "11000000-0000-4000-8000-000000000001" as CompanyId;
export const announcementId = "21000000-0000-4000-8000-000000000001";
export const access = { companyId, permissions: ["announcements.manage"] };
export const summary = (version = 0): AnnouncementSummaryDto => ({
  id: announcementId,
  version,
  title: "Office closure",
  audienceKind: "COMPANY",
  targetCount: 0,
  acknowledgementRequired: true,
  status: "DRAFT",
  recordedAt: "2026-10-11T00:00:00Z",
  publicationJobId: null,
  scheduledFor: null,
  publishedAt: null,
  recipientCount: 0,
  publicationAttempts: 0,
});
export const detail = (version = 0): AnnouncementDto => {
  const { audienceKind, targetCount: _, ...item } = summary(version);
  return {
    ...item,
    body: "The office will be closed on Friday.\nContact your manager.",
    audience: { kind: audienceKind, targetIds: [] },
    reason: "Review office availability",
    recordedBy: "31000000-0000-4000-8000-000000000001",
  };
};
