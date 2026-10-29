import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { PlatformAuditEvent } from "../entities/platform-audit-event";

export function canReadAccountAudit(access: PlatformAccess): boolean {
  return (
    access.permissions.includes("audit.read") && access.permissions.includes("identity.manage")
  );
}

export function isAccountAccessEvent(event: PlatformAuditEvent): boolean {
  return event.resourceType === "account" && event.action === "identity.account_access_saved";
}
