import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type DocumentRevisionChange,
  type DocumentRevisionIntent,
  type DocumentRevisionSnapshot,
  documentRevisionIntents,
} from "../entities/document-revision-action";

export const hasDocumentManagement = (permissions: readonly string[]): boolean =>
  (permissions.includes("documents.manage") && permissions.includes("people.profile.manage")) ||
  permissions.includes("documents.self.upload");

/** Resource ownership, expiry and prior validation eligibility come from the server detail. */
export const canPerformDocumentAction = (
  access: CompanyAccess,
  revision: DocumentRevisionSnapshot,
  intent: DocumentRevisionIntent,
): boolean =>
  access.companyId === revision.companyId &&
  hasDocumentManagement(access.permissions) &&
  documentRevisionIntents.includes(intent) &&
  revision.availableActions.includes(intent === "validate" ? "VALIDATE" : "CANCEL_UPLOAD");

export function prepareDocumentAction(
  access: CompanyAccess,
  operation: OperationId,
  revision: DocumentRevisionSnapshot,
  intent: DocumentRevisionIntent,
  inputReason: string,
): Result<DocumentRevisionChange> {
  if (access.companyId !== revision.companyId || !hasDocumentManagement(access.permissions))
    return failed("access_denied");
  if (!canPerformDocumentAction(access, revision, intent))
    return failed("document_action_unavailable");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(revision.id) ||
    !isUuid(revision.documentId) ||
    !Number.isSafeInteger(revision.version) ||
    revision.version < 0 ||
    revision.version >= Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000
  )
    return failed(
      intent === "validate" ? "invalid_document_validation" : "invalid_document_cancellation",
    );
  return success(
    Object.freeze({ id: revision.id.toLowerCase(), version: revision.version, reason }),
  );
}

export const documentActionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "document_action_unavailable",
    "document_not_found",
    "document_revision_not_found",
    "document_upload_closed",
    "document_upload_incomplete",
    "document_validation_active",
    "document_validation_attempts_exhausted",
    "document_content_expired",
    "document_archived",
  ].includes(failure.code);

/** A rejection releases a first attempt only; an uncertain receipt must keep its original command. */
export const documentActionWasRejected = (failure: Failure): boolean =>
  documentActionReviewChanged(failure) ||
  [
    "invalid_document_validation",
    "invalid_document_cancellation",
    "document_access_denied",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
    "job_queue_full",
    "data_conflict",
  ].includes(failure.code);

export const documentActionAccessLost = (failure: Failure): boolean =>
  [
    "document_access_denied",
    "access_denied",
    "company_access_denied",
    "document_not_found",
    "document_revision_not_found",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);
