import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Document } from "./document";

export const retentionClassifications = ["PERSONAL", "RECEIPT", "HR_ONLY"] as const;
export type DocumentRetentionPolicy = Readonly<{
  id: string;
  companyId: CompanyId;
  classification: Document["classification"];
  version: number;
  retentionDays: number | null;
  actorId: string;
  recordedAt: string;
  reason: string;
}>;
export type DocumentRetentionPolicyPage = Readonly<{
  items: readonly DocumentRetentionPolicy[];
  nextCursor: string | null;
}>;
export type DocumentRetentionPolicySelection = Readonly<{
  companyId: CompanyId;
  classification: Document["classification"];
  policy: DocumentRetentionPolicy | null;
}>;
export type DocumentRetentionPolicyReview = Readonly<{
  selection: DocumentRetentionPolicySelection;
  history: DocumentRetentionPolicyPage;
}>;
export type DocumentRetentionPolicyFields = Readonly<{ retentionDays: string; reason: string }>;
export type DocumentRetentionPolicyChange = Readonly<{
  policyId: string;
  classification: Document["classification"];
  expectedVersion: number | null;
  retentionDays: number | null;
  reason: string;
}>;
