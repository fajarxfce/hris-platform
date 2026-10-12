import type { DocumentRetentionIntent } from "../../domain/entities/document-retention";
import { documentsPath } from "./document-route";

export const documentRetentionRootPath = `${documentsPath}/retention`;
export const documentRetentionPoliciesPath = `${documentRetentionRootPath}/policies`;
export const documentRetentionPolicyPath = (classification: string): string =>
  `${documentRetentionPoliciesPath}/${encodeURIComponent(classification.toLowerCase())}`;
export const isLegacyDocumentRetentionPath = (path: string): boolean =>
  path === `${documentsPath}/retention-policies` ||
  path.startsWith(`${documentsPath}/retention-policies/`);
export const documentRetentionPath = (documentId: string): string =>
  `${documentRetentionRootPath}/documents/${encodeURIComponent(documentId)}`;
export const documentRetentionChangePath = (
  documentId: string,
  intent: DocumentRetentionIntent,
): string => `${documentRetentionPath(documentId)}/${intent}`;
export const isDocumentRetentionPath = (path: string): boolean =>
  isLegacyDocumentRetentionPath(path) ||
  path === documentRetentionRootPath ||
  path.startsWith(`${documentRetentionRootPath}/`);
