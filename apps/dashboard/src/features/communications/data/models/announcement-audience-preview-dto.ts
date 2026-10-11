import { z } from "zod";

export const announcementAudiencePreviewDto = z.object({
  announcementId: z.uuid(),
  version: z.number().int().min(0).max(999),
  asOfDate: z.iso.date(),
  evaluatedAt: z.iso.datetime({ offset: true }),
  recipientCount: z.number().int().min(0).max(5000),
  audienceVersions: z
    .record(z.uuid(), z.number().int().min(0).max(Number.MAX_SAFE_INTEGER))
    .refine((value) => Object.keys(value).length <= 32),
});
export type AnnouncementAudiencePreviewDto = z.infer<typeof announcementAudiencePreviewDto>;
