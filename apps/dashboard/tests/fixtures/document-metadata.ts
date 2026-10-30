import type { DocumentDto } from "../../src/features/documents/data/models/document-dto";
import type { DocumentMetadataPageDto } from "../../src/features/documents/data/models/document-metadata-dto";
import { documentHeader, documentHeaderId, documentId } from "./documents";

export function documentMetadataValue(version = 0) {
  return {
    category: "PASSPORT" as const,
    identityNumber: `P-${version}`,
    issuingCountry: "AU",
    issuedOn: "2010-01-01",
    validUntil: "2020-01-01",
  };
}
export function documentMetadataHeader(version = 0): DocumentDto {
  return {
    ...documentHeader(),
    version: version + 2,
    metadataSupported: true,
    metadataVersion: version,
    metadata: documentMetadataValue(version),
  };
}
export function documentMetadataRevision(version = 0): DocumentMetadataPageDto["items"][number] {
  return {
    documentId: documentHeaderId,
    version,
    documentVersion: version + 2,
    metadata: documentMetadataValue(version),
    actorId: version === 0 ? null : documentId(51),
    recordedAt: "2026-10-01T00:00:00Z",
    reason: version === 0 ? null : "Reviewed document",
  };
}
export const documentMetadataFields = {
  category: "WORK_PERMIT" as const,
  identityNumber: " W-123 ",
  issuingCountry: " au ",
  issuedOn: "2010-01-01",
  validUntil: "2020-01-01",
  reason: "Reviewed permit",
};
