import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type {
  ApprovalTemplate,
  ApprovalTemplateId,
  ApprovalTemplatePage,
  ApprovalTemplateSearch,
} from "../../domain/entities/approval-template";
import type { ApprovalTemplateDto, ApprovalTemplatePageDto } from "../models/approval-template-dto";

export function toApprovalTemplate(
  dto: ApprovalTemplateDto,
  companyId: CompanyId,
): ApprovalTemplate {
  if (!dto.name.trim() || !isCalendarDate(dto.effectiveFrom) || dto.appliedRevision > dto.version)
    throw new InvalidHttpResponseError();
  const stages = dto.stages.map((stage) => {
    const accountIds = stage.accountIds.map((id) => id.toLowerCase() as AccountId);
    if (new Set(accountIds).size !== accountIds.length) throw new InvalidHttpResponseError();
    return Object.freeze({ ...stage, accountIds: Object.freeze(accountIds) });
  });
  const { appliedRevision, ...values } = dto;
  return Object.freeze({
    ...values,
    revision: appliedRevision,
    id: dto.id.toLowerCase() as ApprovalTemplateId,
    companyId,
    stages: Object.freeze(stages),
  });
}
export function toApprovalTemplatePage(
  dto: ApprovalTemplatePageDto,
  company: CompanyId,
  search: ApprovalTemplateSearch,
): ApprovalTemplatePage {
  const items = dto.items.map((item) => toApprovalTemplate(item, company));
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        item.kind !== search.kind ||
        item.effectiveFrom > search.asOf ||
        (search.after !== null && item.id <= search.after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 20 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
