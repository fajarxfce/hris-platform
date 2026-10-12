import type { DocumentRetention } from "./document-retention";
import type { DocumentRevision } from "./document-revision";

export type DocumentRetirementReview = Readonly<{
  revisionId: string;
  revisionNumber: number;
  revisionVersion: number;
  status: DocumentRevision["status"];
  published: boolean;
  retention: DocumentRetention;
  reviewedAt: string;
  blockingReason: string | null;
}>;
export type DocumentRetirementCommand = Readonly<{
  documentId: string;
  revisionId: string;
  expectedRevisionVersion: number;
  expectedRetentionVersion: number;
  reason: string;
}>;
