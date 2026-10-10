import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  ApprovalId,
  ApprovalInbox,
  ApprovalRequest,
} from "../../domain/entities/approval-request";
import type { ApprovalInboxDto, ApprovalRequestDto } from "../models/approval-request-dto";

export function toApprovalRequest(dto: ApprovalRequestDto, companyId: CompanyId): ApprovalRequest {
  const excludedAccountIds = dto.excludedAccountIds.map((id) => id.toLowerCase() as AccountId);
  const stages = dto.stages.map((stage) => {
    const assignees = stage.assignees.map((id) => id.toLowerCase() as AccountId);
    if (new Set(assignees).size !== assignees.length) throw new InvalidHttpResponseError();
    return Object.freeze({ assignees: Object.freeze(assignees) });
  });
  if (
    dto.currentStep >= stages.length ||
    utcInstantMicroseconds(dto.submittedAt) === null ||
    new Set(excludedAccountIds).size !== excludedAccountIds.length
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase() as ApprovalId,
    companyId,
    resourceId: dto.resourceId.toLowerCase(),
    authorId: dto.authorId.toLowerCase() as AccountId,
    requesterId: (dto.requesterId?.toLowerCase() as AccountId | undefined) ?? null,
    templateId: dto.templateId.toLowerCase(),
    stages: Object.freeze(stages),
    excludedAccountIds: Object.freeze(excludedAccountIds),
  });
}

export function toApprovalInbox(
  dto: ApprovalInboxDto,
  company: CompanyId,
  after: string | null,
): ApprovalInbox {
  const items = dto.items.map((item) => toApprovalRequest(item, company));
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        !["PENDING", "BLOCKED"].includes(item.status) ||
        (after !== null && item.id <= after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 20 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
