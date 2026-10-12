import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type DocumentRetentionPolicyChange,
  type DocumentRetentionPolicyFields,
  type DocumentRetentionPolicySelection,
  retentionClassifications,
} from "../entities/document-retention-policy";

export const canManageDocumentRetention = (permissions: readonly string[]): boolean =>
  permissions.includes("documents.retention");
export const validRetentionClassification = (
  value: string,
): value is DocumentRetentionPolicySelection["classification"] =>
  retentionClassifications.includes(value as DocumentRetentionPolicySelection["classification"]);
export const validRetentionCursor = (after: string | null): boolean =>
  after === null || /^(0|[1-9]\d{0,3})$/u.test(after);

export function prepareDocumentRetentionPolicy(
  access: CompanyAccess,
  operation: OperationId,
  selection: DocumentRetentionPolicySelection,
  policyId: string,
  fields: DocumentRetentionPolicyFields,
): Result<DocumentRetentionPolicyChange> {
  if (!canManageDocumentRetention(access.permissions) || access.companyId !== selection.companyId)
    return failed("access_denied");
  const reason = fields.reason.trim(),
    days = fields.retentionDays.trim(),
    current = selection.policy;
  if (
    !isUuid(operation) ||
    !isUuid(policyId) ||
    !validRetentionClassification(selection.classification) ||
    !reason ||
    reason.length > 1000 ||
    /[\p{Cc}]/u.test(reason) ||
    (days !== "" && (!/^\d{1,5}$/u.test(days) || Number(days) < 1 || Number(days) > 36500))
  )
    return failed("invalid_document_retention_policy");
  if (
    current !== null &&
    (current.companyId !== access.companyId ||
      current.id !== policyId.toLowerCase() ||
      current.classification !== selection.classification ||
      !Number.isInteger(current.version) ||
      current.version < 0)
  )
    return failed("invalid_document_retention_policy");
  if (current !== null && current.version >= 9999)
    return failed("document_retention_history_limit");
  return success(
    Object.freeze({
      policyId: policyId.toLowerCase(),
      classification: selection.classification,
      expectedVersion: current?.version ?? null,
      retentionDays: days === "" ? null : Number(days),
      reason,
    }),
  );
}
