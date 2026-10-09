import type { AuditPageDto } from "../models/audit-page-dto";
import type { AuditSearchDto } from "../models/audit-search-dto";

export interface AuditDataSource {
  search(companyId: string, query: AuditSearchDto, signal: AbortSignal): Promise<AuditPageDto>;
}
