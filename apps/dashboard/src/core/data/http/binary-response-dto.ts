import { z } from "zod";
import { binaryFilePartBytes, maximumBinaryFileBytes } from "../../domain/files/binary-file";

export const binaryResponseDto = z.object({
  parts: z
    .array(z.instanceof(ArrayBuffer))
    .min(1)
    .max(maximumBinaryFileBytes / binaryFilePartBytes),
  byteLength: z.number().int().min(1).max(maximumBinaryFileBytes),
  mediaType: z.string().min(1).max(200),
  etag: z.string().min(1).max(512),
});
export type BinaryResponseDto = z.infer<typeof binaryResponseDto>;
