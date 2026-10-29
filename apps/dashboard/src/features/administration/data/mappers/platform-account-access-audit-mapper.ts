import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { PlatformAccountAccessAudit } from "../../domain/entities/platform-account-access-audit";
import type { PlatformAuditEvent } from "../../domain/entities/platform-audit-event";
import type { PlatformAccountAccessAuditDto } from "../models/platform-account-access-audit-dto";

export function toPlatformAccountAccessAudit(
  dto: PlatformAccountAccessAuditDto,
  event: PlatformAuditEvent,
): PlatformAccountAccessAudit {
  if (dto.id !== event.id || dto.accountId !== event.resourceId)
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto, permissions: Object.freeze([...dto.permissions]) });
}
