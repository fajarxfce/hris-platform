import type { AnnouncementId } from "./announcement";

export type AnnouncementAudiencePreview = Readonly<{
  announcementId: AnnouncementId;
  version: number;
  asOfDate: string;
  evaluatedAt: string;
  recipientCount: number;
  references: readonly Readonly<{ id: string; version: number }>[];
}>;
