import type { AuditSearchDto } from "../models/audit-search-dto";
import type { PlatformAuditPageDto } from "../models/platform-audit-page-dto";

export interface PlatformAuditDataSource {
  search(query: AuditSearchDto, signal: AbortSignal): Promise<PlatformAuditPageDto>;
}
