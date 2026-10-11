import { z } from "zod";

const common = {
  id: z.uuid(),
  version: z.number().int().min(0).max(999),
  name: z.string().min(1).max(120),
  active: z.boolean(),
  recordedAt: z.iso.datetime({ offset: true }),
};
export const audienceGroupSummaryDto = z.object({
  ...common,
  memberCount: z.number().int().min(0).max(5000),
});
export const audienceGroupDto = z.object({
  ...common,
  employmentIds: z.array(z.uuid()).max(5000),
  recordedBy: z.uuid(),
  reason: z.string().min(1).max(1000),
});
export const audienceGroupPageDto = z.object({
  items: z.array(audienceGroupSummaryDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export type AudienceGroupDto = z.infer<typeof audienceGroupDto>;
export type AudienceGroupPageDto = z.infer<typeof audienceGroupPageDto>;
