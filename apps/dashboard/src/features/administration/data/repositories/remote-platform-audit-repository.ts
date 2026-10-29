import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { type AuditSearch, auditPageSize } from "../../domain/entities/audit-search";
import type { PlatformAuditEvent } from "../../domain/entities/platform-audit-event";
import type { PlatformAuditRepository } from "../../domain/repositories/platform-audit-repository";
import type { PlatformAuditDataSource } from "../datasources/platform-audit-data-source";
import { toPlatformAccountAccessAudit } from "../mappers/platform-account-access-audit-mapper";
import { toPlatformAuditPage } from "../mappers/platform-audit-page-mapper";

export class RemotePlatformAuditRepository implements PlatformAuditRepository {
  constructor(private readonly source: PlatformAuditDataSource) {}

  accountAccess(event: PlatformAuditEvent, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPlatformAccountAccessAudit(await this.source.accountAccess(event.id, signal), event),
    );
  }

  search(query: AuditSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPlatformAuditPage(
        await this.source.search({ ...query, limit: auditPageSize }, signal),
        query,
      ),
    );
  }
}
