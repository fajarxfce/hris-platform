import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { PlatformAuditEvent } from "../entities/platform-audit-event";
import {
  canReadAccountAudit,
  isAccountAccessEvent,
} from "../policies/platform-account-audit-policy";
import type { PlatformAuditRepository } from "../repositories/platform-audit-repository";

export class GetPlatformAccountAccessAudit {
  constructor(private readonly audits: PlatformAuditRepository) {}

  execute(access: PlatformAccess, event: PlatformAuditEvent, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAccountAudit(access)) return Promise.resolve(failed("access_denied"));
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
    if (!isAccountAccessEvent(event) || !uuid.test(event.id) || !uuid.test(event.resourceId))
      return Promise.resolve(failed("audit_detail_not_found"));
    return this.audits.accountAccess(event, signal);
  }
}
