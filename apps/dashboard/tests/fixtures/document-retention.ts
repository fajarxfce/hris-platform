import type { DocumentRetentionPolicyDto } from "../../src/features/documents/data/models/document-retention-dto";
import type {
  DocumentRetentionChangeDto,
  DocumentRetentionDto,
} from "../../src/features/documents/data/models/document-retention-state-dto";
import { documentHeaderId, documentId } from "./documents";

export const retentionPolicyId = documentId(700);
export function retentionPolicy(version = 0): DocumentRetentionPolicyDto {
  return {
    id: retentionPolicyId,
    classification: "PERSONAL",
    version,
    retentionDays: 365,
    actorId: documentId(51),
    recordedAt: "2026-10-12T00:00:00Z",
    reason: "Document retention policy",
  };
}

export function documentRetention(version: number | null = null): DocumentRetentionDto {
  return { documentId: documentHeaderId, version, archive: null, legalHold: false };
}
export function archivedDocumentRetention(version = 0): DocumentRetentionDto {
  return {
    ...documentRetention(version),
    archive: {
      archivedAt: "2024-10-01T00:00:00Z",
      policyId: retentionPolicyId,
      policyVersion: 0,
      retentionDays: 365,
      eligibleAt: "2025-10-01T00:00:00Z",
    },
  };
}
export function documentRetentionChange(version = 0): DocumentRetentionChangeDto {
  return {
    state: version % 2 === 0 ? archivedDocumentRetention(version) : documentRetention(version),
    action: version % 2 === 0 ? "ARCHIVE" : "RESTORE",
    actorId: documentId(51),
    recordedAt: "2026-10-12T00:00:00Z",
    reason: "Reviewed document lifecycle",
    retiredRevisionId: null,
  };
}
