import type { CompanyId } from "../../../../core/domain/identifiers";

export type AnnouncementId = string & { readonly announcementId: unique symbol };
export type AnnouncementStatus = "DRAFT" | "QUEUED" | "PUBLISHED" | "ARCHIVED";
export type AudienceKind = "COMPANY" | "BRANCH" | "DEPARTMENT" | "GROUP";
export type AnnouncementSummary = Readonly<{
  id: AnnouncementId;
  companyId: CompanyId;
  version: number;
  title: string;
  audienceKind: AudienceKind;
  targetCount: number;
  acknowledgementRequired: boolean;
  status: AnnouncementStatus;
  recordedAt: string;
  publicationJobId: string | null;
  scheduledFor: string | null;
  publishedAt: string | null;
  recipientCount: number;
  publicationAttempts: number;
}>;
export type Announcement = AnnouncementSummary &
  Readonly<{
    body: string;
    targetIds: readonly string[];
    recordedBy: string;
    reason: string;
  }>;
