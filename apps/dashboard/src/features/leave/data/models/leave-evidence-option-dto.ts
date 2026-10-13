import { z } from "zod";
import { leaveInstantDto } from "./leave-value-dto";

export const leaveEvidenceOptionsDto = z.object({
  items: z
    .array(
      z.object({
        documentId: z.uuid(),
        revisionId: z.uuid(),
        revisionNumber: z.number().int().min(1).max(100),
        title: z.string().min(1).max(160),
        fileName: z
          .string()
          .min(1)
          .max(180)
          .regex(/^[^\p{Cc}/\\]+$/u),
        mediaType: z.enum(["application/pdf", "image/jpeg", "image/png"]),
        size: z
          .number()
          .int()
          .min(1)
          .max(100 * 1024 * 1024),
        validatedAt: leaveInstantDto,
      }),
    )
    .max(20),
  nextCursor: z.uuid().nullable(),
});
export type LeaveEvidenceOptionsDto = z.infer<typeof leaveEvidenceOptionsDto>;
