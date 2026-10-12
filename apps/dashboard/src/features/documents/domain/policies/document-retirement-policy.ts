import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DocumentRetirementCommand,
  DocumentRetirementReview,
} from "../entities/document-retirement";
import { canManageDocumentRetention } from "./document-retention-policy";

/** The server owns elapsed-time, evidence and hold eligibility; retries retain this review. */
export function prepareDocumentRetirement(
  access: CompanyAccess,
  operation: OperationId,
  review: DocumentRetirementReview,
  inputReason: string,
): Result<DocumentRetirementCommand> {
  if (
    !canManageDocumentRetention(access.permissions) ||
    access.companyId !== review.retention.companyId
  )
    return failed("access_denied");
  const reason = inputReason.trim(),
    version = review.retention.version;
  if (
    !isUuid(operation) ||
    !isUuid(review.revisionId) ||
    !isUuid(review.retention.documentId) ||
    !Number.isSafeInteger(review.revisionVersion) ||
    review.revisionVersion < 0 ||
    review.revisionVersion >= Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000 ||
    /[\p{Cc}]/u.test(reason)
  )
    return failed("invalid_document_retirement");
  if (review.blockingReason !== null) return failed(review.blockingReason);
  if (
    version === null ||
    !Number.isInteger(version) ||
    version < 0 ||
    version >= 9999 ||
    review.status !== "READY" ||
    !review.retention.archive ||
    review.retention.legalHold
  )
    return failed("document_retirement_unavailable");
  return success(
    Object.freeze({
      documentId: review.retention.documentId.toLowerCase(),
      revisionId: review.revisionId.toLowerCase(),
      expectedRevisionVersion: review.revisionVersion,
      expectedRetentionVersion: version,
      reason,
    }),
  );
}
