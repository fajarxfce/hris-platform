import type { DocumentRetirementReviewDto } from "../../src/features/documents/data/models/document-retirement-dto";
import { archivedDocumentRetention } from "./document-retention";
import { documentRevisionId } from "./documents";

export function documentRetirementReview(): DocumentRetirementReviewDto {
  return {
    revisionId: documentRevisionId,
    revisionNumber: 1,
    revisionVersion: 3,
    status: "READY",
    published: true,
    retention: archivedDocumentRetention(),
    reviewedAt: "2026-10-12T00:00:00Z",
    blockingReason: null,
  };
}
