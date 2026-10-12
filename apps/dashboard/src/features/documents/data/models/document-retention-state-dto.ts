import { z } from "zod";

const instant = z.iso.datetime({ offset: true }).max(40),
  version = z.number().int().min(0).max(9999);
export const documentArchiveDto = z.object({
  archivedAt: instant,
  policyId: z.uuid().nullable(),
  policyVersion: version.nullable(),
  retentionDays: z.number().int().min(1).max(36500).nullable(),
  eligibleAt: instant.nullable(),
});
export const documentRetentionDto = z.object({
  documentId: z.uuid(),
  version: version.nullable(),
  archive: documentArchiveDto.nullable(),
  legalHold: z.boolean(),
});
export const documentRetentionChangeDto = z.object({
  state: documentRetentionDto,
  action: z.enum(["ARCHIVE", "RESTORE", "PLACE_HOLD", "RELEASE_HOLD", "RETIRE"]),
  actorId: z.uuid(),
  recordedAt: instant,
  reason: z
    .string()
    .min(1)
    .max(1000)
    .regex(/^[^\p{Cc}]+$/u),
  retiredRevisionId: z.uuid().nullable(),
});
export const documentRetentionHistoryDto = z.object({
  items: z.array(documentRetentionChangeDto).max(20),
  nextCursor: z
    .string()
    .regex(/^(0|[1-9]\d{0,3})$/u)
    .nullable(),
});
export type DocumentRetentionDto = z.infer<typeof documentRetentionDto>;
export type DocumentRetentionChangeDto = z.infer<typeof documentRetentionChangeDto>;
export type DocumentRetentionHistoryDto = z.infer<typeof documentRetentionHistoryDto>;
export type DocumentRetentionCommandDto = Readonly<{
  documentId: string;
  intent: "archive" | "restore" | "hold" | "release-hold";
  expectedVersion: number | null;
  reason: string;
}>;
