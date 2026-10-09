import { failed, type Result, success } from "../../../../core/domain/result";
import type { AuditSearch } from "../entities/audit-search";
import { auditInstantMicros } from "./audit-time";

export function canReadAudit(permissions: readonly string[]): boolean {
  return permissions.includes("audit.read");
}

export function validateAuditSearch(search: AuditSearch): Result<AuditSearch> {
  const from = search.from === null ? null : auditInstantMicros(search.from);
  const until = search.until === null ? null : auditInstantMicros(search.until);
  if (
    (search.from !== null && from === null) ||
    (search.until !== null && until === null) ||
    (from !== null && until !== null && (from >= until || until - from > 90n * 86_400_000_000n))
  )
    return failed("invalid_audit_range");
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu;
  if (search.cursor !== null && (!uuid.test(search.cursor) || from === null || until === null))
    return failed("invalid_audit_cursor");
  if (
    (search.actorId !== null && !uuid.test(search.actorId)) ||
    (search.resourceId !== null && !uuid.test(search.resourceId)) ||
    (search.resourceType !== null && !/^[a-z][a-z0-9_.:-]{0,79}$/u.test(search.resourceType)) ||
    (search.action !== null && !/^[a-z][a-z0-9_.:-]{0,99}$/u.test(search.action))
  )
    return failed("invalid_audit_filter");
  return success(
    Object.freeze({
      ...search,
      cursor: search.cursor?.toLowerCase() ?? null,
      actorId: search.actorId?.toLowerCase() ?? null,
      resourceId: search.resourceId?.toLowerCase() ?? null,
    }),
  );
}
