import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { AudienceGroupChange } from "../entities/audience-group-change";
import { communicationsCommandWasRejected } from "./communications-command-policy";

export function normalizeAudienceGroupChange(input: AudienceGroupChange): AudienceGroupChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    name: input.name.trim(),
    reason: input.reason.trim(),
    employmentIds: Object.freeze(input.employmentIds.map((id) => id.toLowerCase()).sort()),
  });
}
export function validateAudienceGroupChange(input: AudienceGroupChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 999)
  )
    fields.expectedVersion = "out_of_range";
  if (!input.name.length || input.name.length > 120 || /\p{Cc}/u.test(input.name))
    fields.name = "invalid_text";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_text";
  if (
    input.employmentIds.length > 5000 ||
    new Set(input.employmentIds).size !== input.employmentIds.length ||
    input.employmentIds.some((id) => !isUuid(id))
  )
    fields.employmentIds = "invalid_selection";
  return Object.keys(fields).length
    ? { code: "invalid_audience_group", fields, parameters: {} }
    : null;
}
export const audienceGroupSaveWasRejected = (failure: Failure): boolean =>
  communicationsCommandWasRejected(failure) ||
  [
    "invalid_audience_group",
    "audience_group_employee_unavailable",
    "audience_group_not_found",
    "audience_group_limit",
    "audience_group_revision_limit",
  ].includes(failure.code);
