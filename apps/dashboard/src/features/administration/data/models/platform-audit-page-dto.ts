import { z } from "zod";

export const platformAuditPageDto = z.strictObject({
  from: z.iso.datetime(),
  until: z.iso.datetime(),
  evaluatedAt: z.iso.datetime(),
  items: z
    .array(
      z.strictObject({
        id: z.uuid(),
        actorId: z.uuid(),
        resourceType: z.string().min(1).max(80),
        resourceId: z.uuid(),
        action: z.string().min(1).max(100),
        correlationId: z.uuid(),
        recordedAt: z.iso.datetime(),
      }),
    )
    .max(200),
  nextCursor: z.uuid().nullable(),
});

export type PlatformAuditPageDto = z.infer<typeof platformAuditPageDto>;
