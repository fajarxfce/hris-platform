import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { AnnouncementChange } from "../entities/announcement-change";

export function normalizeAnnouncementChange(input: AnnouncementChange): AnnouncementChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    title: input.title.trim(),
    body: input.body.replace(/\r\n?/gu, "\n").trim(),
    reason: input.reason.trim(),
    targetIds: Object.freeze(input.targetIds.map((id) => id.toLowerCase()).sort()),
  });
}
export function validateAnnouncementChange(input: AnnouncementChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER)
  )
    fields.expectedVersion = "out_of_range";
  if (!input.title.length || input.title.length > 200 || /\p{Cc}/u.test(input.title))
    fields.title = "invalid_text";
  if (
    !input.body.length ||
    input.body.length > 16000 ||
    /\p{Cc}/u.test(input.body.replace(/[\n\t]/gu, ""))
  )
    fields.body = "invalid_text";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_text";
  if (!["COMPANY", "BRANCH", "DEPARTMENT", "GROUP"].includes(input.audienceKind))
    fields["audience.kind"] = "invalid_selection";
  if (input.audienceKind === "COMPANY") {
    if (input.targetIds.length !== 0) fields["audience.targetIds"] = "must_be_empty";
  } else if (
    !input.targetIds.length ||
    input.targetIds.length > 32 ||
    new Set(input.targetIds).size !== input.targetIds.length ||
    input.targetIds.some((id) => !isUuid(id))
  )
    fields["audience.targetIds"] = "invalid_selection";
  return Object.keys(fields).length
    ? { code: "invalid_announcement", fields, parameters: {} }
    : null;
}

/** A later rejection never resolves an earlier request whose committed response was lost. */
export const announcementSaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_announcement",
    "invalid_version",
    "announcement_audience_unavailable",
    "announcement_not_draft",
    "announcement_not_found",
    "announcement_revision_limit",
    "stale_version",
    "data_conflict",
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
    "request_body_too_large",
  ].includes(failure.code);
