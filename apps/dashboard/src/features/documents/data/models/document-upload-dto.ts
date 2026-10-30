import { z } from "zod";
import type { DocumentBusinessMetadataDto } from "./document-metadata-dto";

export type DocumentUploadStartDto = Readonly<{
  documentId: string;
  revisionId: string;
  employmentId: string;
  title: string;
  classification: "PERSONAL" | "RECEIPT" | "HR_ONLY";
  expectedDocumentVersion: number;
  fileName: string;
  mediaType: "application/pdf" | "image/png" | "image/jpeg";
  size: number;
  sha256: string;
  reason: string;
  metadata?: DocumentBusinessMetadataDto;
}>;
export const documentUploadReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type DocumentUploadReceiptDto = z.infer<typeof documentUploadReceiptDto>;
