import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { AuditSearch } from "../entities/audit-search";
import { canReadAudit, validateAuditSearch } from "../policies/audit-search-policy";
import type { PlatformAuditRepository } from "../repositories/platform-audit-repository";

export class SearchPlatformAuditEvents {
  constructor(private readonly audits: PlatformAuditRepository) {}

  execute(access: PlatformAccess, query: AuditSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAudit(access.permissions)) return Promise.resolve(failed("access_denied"));
    const prepared = validateAuditSearch(query);
    if (!prepared.ok) return Promise.resolve(prepared);
    return this.audits.search(prepared.value, signal);
  }
}
