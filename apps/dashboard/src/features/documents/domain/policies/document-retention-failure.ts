import type { Failure } from "../../../../core/domain/result";
import { documentActionAccessLost, documentActionWasRejected } from "./document-action-policy";

export const documentRetentionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "document_retention_policy_not_found",
    "document_retention_policy_exists",
    "document_retention_classification_immutable",
    "document_retention_history_limit",
    "document_retention_action_unavailable",
    "document_already_archived",
    "document_not_archived",
    "document_already_held",
    "document_not_held",
    "document_upload_active",
  ].includes(failure.code);

export const documentRetentionWasRejected = (failure: Failure): boolean =>
  documentRetentionReviewChanged(failure) ||
  documentActionWasRejected(failure) ||
  ["invalid_document_retention_policy", "invalid_document_retention_change"].includes(failure.code);

export const documentRetentionAccessLost = (failure: Failure): boolean =>
  documentActionAccessLost(failure) || failure.code === "document_retention_policy_not_found";
