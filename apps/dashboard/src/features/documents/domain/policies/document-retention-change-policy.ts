import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type DocumentRetention,
  type DocumentRetentionCommand,
  type DocumentRetentionIntent,
  documentRetentionIntents,
} from "../entities/document-retention";
import { canManageDocumentRetention } from "./document-retention-policy";

export const validDocumentRetentionIntent = (intent: string): intent is DocumentRetentionIntent =>
  documentRetentionIntents.includes(intent as DocumentRetentionIntent);

/** Upload completion and archive-policy selection are checked under server guards. */
export function availableDocumentRetentionIntents(
  state: DocumentRetention,
): readonly DocumentRetentionIntent[] {
  if ((state.version ?? -1) >= 9999) return [];
  return Object.freeze([
    state.archive ? "restore" : "archive",
    state.legalHold ? "release-hold" : "hold",
  ]);
}
export function prepareDocumentRetentionChange(
  access: CompanyAccess,
  operation: OperationId,
  state: DocumentRetention,
  intent: DocumentRetentionIntent,
  inputReason: string,
): Result<DocumentRetentionCommand> {
  if (!canManageDocumentRetention(access.permissions) || access.companyId !== state.companyId)
    return failed("access_denied");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(state.documentId) ||
    !validDocumentRetentionIntent(intent) ||
    (state.version !== null && (!Number.isInteger(state.version) || state.version < 0)) ||
    !reason ||
    reason.length > 1000 ||
    /[\p{Cc}]/u.test(reason)
  )
    return failed("invalid_document_retention_change");
  if ((state.version ?? -1) >= 9999) return failed("document_retention_history_limit");
  if (!availableDocumentRetentionIntents(state).includes(intent))
    return failed("document_retention_action_unavailable");
  return success(
    Object.freeze({
      documentId: state.documentId.toLowerCase(),
      intent,
      expectedVersion: state.version,
      reason,
    }),
  );
}
