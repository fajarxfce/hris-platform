import type { Result } from "../../../../core/domain/result";
import type { AuditSearch } from "../entities/audit-search";
import type { PlatformAccountAccessAudit } from "../entities/platform-account-access-audit";
import type { PlatformAuditEvent } from "../entities/platform-audit-event";
import type { PlatformAuditPage } from "../entities/platform-audit-page";

export interface PlatformAuditRepository {
  accountAccess(
    event: PlatformAuditEvent,
    signal: AbortSignal,
  ): Promise<Result<PlatformAccountAccessAudit>>;
  search(query: AuditSearch, signal: AbortSignal): Promise<Result<PlatformAuditPage>>;
}
