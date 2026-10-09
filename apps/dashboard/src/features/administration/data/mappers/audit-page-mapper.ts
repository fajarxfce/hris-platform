import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { AuditPage } from "../../domain/entities/audit-page";
import { type AuditSearch, auditPageSize } from "../../domain/entities/audit-search";
import { auditInstantMicros } from "../../domain/policies/audit-time";
import type { AuditPageDto } from "../models/audit-page-dto";

export function toAuditPage(
  dto: AuditPageDto,
  companyId: CompanyId,
  query: AuditSearch,
): AuditPage {
  const from = auditInstantMicros(dto.from);
  const until = auditInstantMicros(dto.until);
  const evaluated = auditInstantMicros(dto.evaluatedAt);
  if (
    dto.companyId !== companyId ||
    from === null ||
    until === null ||
    evaluated === null ||
    from >= until ||
    until > evaluated ||
    until - from > 90n * 86_400_000_000n ||
    (query.from !== null && auditInstantMicros(query.from) !== from) ||
    (query.until !== null && auditInstantMicros(query.until) !== until) ||
    (query.from === null && until - from !== 30n * 86_400_000_000n) ||
    dto.items.length > auditPageSize ||
    (dto.nextCursor !== null &&
      (dto.items.length !== auditPageSize || dto.nextCursor !== dto.items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  const ids = new Set<string>();
  let previous: { time: bigint; id: string } | null = null;
  const items = dto.items.map((item) => {
    const time = auditInstantMicros(item.recordedAt);
    if (
      item.companyId !== companyId ||
      ids.has(item.id) ||
      item.id === query.cursor ||
      time === null ||
      time < from ||
      time >= until ||
      (previous && (time > previous.time || (time === previous.time && item.id >= previous.id))) ||
      (query.actorId !== null && query.actorId !== item.actorId) ||
      (query.resourceId !== null && query.resourceId !== item.resourceId) ||
      (query.resourceType !== null && query.resourceType !== item.resourceType) ||
      (query.action !== null && query.action !== item.action)
    )
      throw new InvalidHttpResponseError();
    ids.add(item.id);
    previous = { time, id: item.id };
    return Object.freeze({ ...item, companyId, actorId: item.actorId as AccountId });
  });
  return Object.freeze({ ...dto, companyId, items: Object.freeze(items) });
}
