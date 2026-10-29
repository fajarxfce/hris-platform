import type { AuditSearchDto } from "../models/audit-search-dto";
import type { PlatformAccountAccessAuditDto } from "../models/platform-account-access-audit-dto";
import type { PlatformAuditPageDto } from "../models/platform-audit-page-dto";

export interface PlatformAuditDataSource {
  accountAccess(id: string, signal: AbortSignal): Promise<PlatformAccountAccessAuditDto>;
  search(query: AuditSearchDto, signal: AbortSignal): Promise<PlatformAuditPageDto>;
}
