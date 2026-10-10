import { z } from "zod";

export const leaveAttachmentDto = z.object({
  documentId: z.uuid(),
  revisionId: z.uuid(),
  fileName: z.string().min(1).max(255),
  mediaType: z.string().min(1).max(200),
  size: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  sha256: z.string().regex(/^[0-9a-f]{64}$/u),
});
