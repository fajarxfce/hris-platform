import { documentsPath } from "./document-route";

export const documentRetentionPoliciesPath = `${documentsPath}/retention-policies`;
export const documentRetentionPolicyPath = (classification: string): string =>
  `${documentRetentionPoliciesPath}/${encodeURIComponent(classification.toLowerCase())}`;
export const isDocumentRetentionPolicyPath = (path: string): boolean =>
  path === documentRetentionPoliciesPath || path.startsWith(`${documentRetentionPoliciesPath}/`);
