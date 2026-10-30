import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Document } from "./document";

export const documentCategories = [
  "PASSPORT",
  "NATIONAL_ID",
  "WORK_PERMIT",
  "RESIDENCE_PERMIT",
  "EMPLOYMENT_CONTRACT",
  "CERTIFICATE",
  "RECEIPT",
  "OTHER",
] as const;
export type DocumentCategory = (typeof documentCategories)[number];
export type DocumentBusinessMetadata = Readonly<{
  category: DocumentCategory;
  identityNumber: string | null;
  issuingCountry: string | null;
  issuedOn: string | null;
  validUntil: string | null;
}>;
export type DocumentMetadataFields = Readonly<{
  category: DocumentCategory;
  identityNumber: string;
  issuingCountry: string;
  issuedOn: string;
  validUntil: string;
  reason: string;
}>;
export type DocumentMetadataChange = Readonly<{
  documentId: string;
  expectedVersion: number;
  metadata: DocumentBusinessMetadata;
  reason: string;
}>;
export type DocumentMetadataRevision = Readonly<{
  companyId: CompanyId;
  documentId: string;
  version: number;
  documentVersion: number;
  metadata: DocumentBusinessMetadata;
  actorId: string | null;
  recordedAt: string;
  reason: string | null;
}>;
export type DocumentMetadataPage = Readonly<{
  items: readonly DocumentMetadataRevision[];
  nextCursor: string | null;
}>;
export type DocumentMetadataReview = Readonly<{
  document: Document;
  history: DocumentMetadataPage;
}>;
