import { z } from "zod";

export const audienceReferenceDto = z.object({
  id: z.uuid(),
  kind: z.enum(["BRANCH", "DEPARTMENT", "GROUP", "EMPLOYMENT"]),
  name: z.string().min(1).max(200),
  code: z.string().min(1).max(32).nullable(),
  version: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
  active: z.boolean().nullable(),
});
export const audienceReferencePageDto = z.object({
  items: z.array(audienceReferenceDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export type AudienceReferencePageDto = z.infer<typeof audienceReferencePageDto>;
export type AudienceReferenceQueryDto = Readonly<{
  kind: string;
  query: string;
  ids: readonly string[];
  after: string | null;
}>;
