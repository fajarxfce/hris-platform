import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AuditSearch } from "../entities/audit-search";
import { canReadAudit, validateAuditSearch } from "../policies/audit-search-policy";
import type { AuditRepository } from "../repositories/audit-repository";

export class SearchAuditEvents {
  constructor(private readonly audits: AuditRepository) {}

  execute(access: CompanyAccess, query: AuditSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadAudit(access.permissions)) return Promise.resolve(failed("access_denied"));
    const prepared = validateAuditSearch(query);
    if (!prepared.ok) return Promise.resolve(prepared);
    return this.audits.search(access.companyId, prepared.value, signal);
  }
}
