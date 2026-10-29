import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { type AuditSearch, auditPageSize } from "../../domain/entities/audit-search";
import type { PlatformAuditRepository } from "../../domain/repositories/platform-audit-repository";
import type { PlatformAuditDataSource } from "../datasources/platform-audit-data-source";
import { toPlatformAuditPage } from "../mappers/platform-audit-page-mapper";

export class RemotePlatformAuditRepository implements PlatformAuditRepository {
  constructor(private readonly source: PlatformAuditDataSource) {}

  search(query: AuditSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPlatformAuditPage(
        await this.source.search({ ...query, limit: auditPageSize }, signal),
        query,
      ),
    );
  }
}
