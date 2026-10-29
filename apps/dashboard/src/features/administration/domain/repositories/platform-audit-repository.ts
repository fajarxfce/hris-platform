import type { Result } from "../../../../core/domain/result";
import type { AuditSearch } from "../entities/audit-search";
import type { PlatformAuditPage } from "../entities/platform-audit-page";

export interface PlatformAuditRepository {
  search(query: AuditSearch, signal: AbortSignal): Promise<Result<PlatformAuditPage>>;
}
