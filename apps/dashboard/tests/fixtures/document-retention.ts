import type { DocumentRetentionPolicyDto } from "../../src/features/documents/data/models/document-retention-dto";
import { documentId } from "./documents";

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
