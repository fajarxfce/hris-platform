import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { AnnouncementCommand } from "../entities/announcement-command";
import { communicationsCommandWasRejected } from "./communications-command-policy";

export function normalizeAnnouncementCommand(input: AnnouncementCommand): AnnouncementCommand {
  return Object.freeze({ ...input, id: input.id.toLowerCase(), reason: input.reason.trim() });
}
export function validateAnnouncementCommand(input: AnnouncementCommand): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion > 998
  )
    fields.expectedVersion = "out_of_range";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_text";
  if (
    input.action === "PUBLISH" &&
    input.scheduledFor !== null &&
    (utcInstantMicroseconds(input.scheduledFor) === null ||
      !/T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/u.test(input.scheduledFor))
  )
    fields.scheduledFor = "invalid_instant";
  return Object.keys(fields).length
    ? { code: "invalid_announcement_command", fields, parameters: {} }
    : null;
}
export const announcementCommandWasRejected = (failure: Failure): boolean =>
  communicationsCommandWasRejected(failure) ||
  [
    "invalid_announcement_command",
    "invalid_announcement_schedule",
    "announcement_not_found",
    "announcement_not_draft",
    "announcement_archived",
    "announcement_revision_limit",
    "announcement_attempt_limit",
    "announcement_audience_unavailable",
    "announcement_publication_active",
    "announcement_publication_not_stopped",
    "job_queue_full",
  ].includes(failure.code);
