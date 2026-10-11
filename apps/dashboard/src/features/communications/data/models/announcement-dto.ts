import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true });
const audience = z.enum(["COMPANY", "BRANCH", "DEPARTMENT", "GROUP"]);
const common = {
  id: z.uuid(),
  version,
  title: z.string().min(1).max(200),
  acknowledgementRequired: z.boolean(),
  status: z.enum(["DRAFT", "QUEUED", "PUBLISHED", "ARCHIVED"]),
  recordedAt: instant,
  publicationJobId: z.uuid().nullable(),
  scheduledFor: instant.nullable(),
  publishedAt: instant.nullable(),
  recipientCount: z.number().int().min(0).max(5000),
  publicationAttempts: z.number().int().min(0).max(8),
};
export const announcementSummaryDto = z.object({
  ...common,
  audienceKind: audience,
  targetCount: z.number().int().min(0).max(32),
});
export const announcementDto = z.object({
  ...common,
  body: z.string().min(1).max(16000),
  audience: z.object({ kind: audience, targetIds: z.array(z.uuid()).max(32) }),
  recordedBy: z.uuid(),
  reason: z.string().min(1).max(1000),
});
export const announcementPageDto = z.object({
  items: z.array(announcementSummaryDto).max(50),
  nextCursor: z.string().min(1).max(36).nullable(),
});
export type AnnouncementDto = z.infer<typeof announcementDto>;
export type AnnouncementSummaryDto = z.infer<typeof announcementSummaryDto>;
export type AnnouncementPageDto = z.infer<typeof announcementPageDto>;
