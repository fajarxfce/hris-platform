import type { CompanyId } from "../../../../core/domain/identifiers";

export type DocumentArchive = Readonly<{
  archivedAt: string;
  policyId: string | null;
  policyVersion: number | null;
  retentionDays: number | null;
  eligibleAt: string | null;
}>;
export type DocumentRetention = Readonly<{
  companyId: CompanyId;
  documentId: string;
  version: number | null;
  archive: DocumentArchive | null;
  legalHold: boolean;
}>;
export const documentRetentionIntents = ["archive", "restore", "hold", "release-hold"] as const;
export type DocumentRetentionIntent = (typeof documentRetentionIntents)[number];
export type DocumentRetentionChange = Readonly<{
  state: DocumentRetention;
  action: "ARCHIVE" | "RESTORE" | "PLACE_HOLD" | "RELEASE_HOLD" | "RETIRE";
  actorId: string;
  recordedAt: string;
  reason: string;
  retiredRevisionId: string | null;
}>;
export type DocumentRetentionHistory = Readonly<{
  items: readonly DocumentRetentionChange[];
  nextCursor: string | null;
}>;
export type DocumentRetentionReview = Readonly<{
  current: DocumentRetention;
  history: DocumentRetentionHistory;
}>;
export type DocumentRetentionCommand = Readonly<{
  documentId: string;
  intent: DocumentRetentionIntent;
  expectedVersion: number | null;
  reason: string;
}>;
