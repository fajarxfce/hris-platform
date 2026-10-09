import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { type AuditSearch, auditPageSize } from "../../domain/entities/audit-search";
import type { AuditRepository } from "../../domain/repositories/audit-repository";
import type { AuditDataSource } from "../datasources/audit-data-source";
import { toAuditPage } from "../mappers/audit-page-mapper";

export class RemoteAuditRepository implements AuditRepository {
  constructor(private readonly source: AuditDataSource) {}

  search(companyId: CompanyId, query: AuditSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toAuditPage(
        await this.source.search(companyId, { ...query, limit: auditPageSize }, signal),
        companyId,
        query,
      ),
    );
  }
}
