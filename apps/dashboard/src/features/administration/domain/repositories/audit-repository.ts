import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { AuditPage } from "../entities/audit-page";
import type { AuditSearch } from "../entities/audit-search";

export interface AuditRepository {
  search(companyId: CompanyId, query: AuditSearch, signal: AbortSignal): Promise<Result<AuditPage>>;
}
