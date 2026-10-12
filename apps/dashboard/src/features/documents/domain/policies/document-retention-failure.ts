import type { Failure } from "../../../../core/domain/result";
import { documentActionAccessLost, documentActionWasRejected } from "./document-action-policy";

export const documentRetentionReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "document_retention_policy_not_found",
    "document_retention_policy_exists",
    "document_retention_classification_immutable",
    "document_retention_history_limit",
  ].includes(failure.code);

export const documentRetentionWasRejected = (failure: Failure): boolean =>
  documentRetentionReviewChanged(failure) ||
  documentActionWasRejected(failure) ||
  failure.code === "invalid_document_retention_policy";

export const documentRetentionAccessLost = (failure: Failure): boolean =>
  documentActionAccessLost(failure) || failure.code === "document_retention_policy_not_found";
