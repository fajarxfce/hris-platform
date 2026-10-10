import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { ApprovalAssigneePage } from "../../domain/entities/approval-assignee";
import type { ApprovalAssigneePageDto } from "../models/approval-assignee-dto";

export function toApprovalAssigneePage(
  dto: ApprovalAssigneePageDto,
  after: string | null,
): ApprovalAssigneePage {
  const items = dto.items.map((item) =>
    Object.freeze({ ...item, id: item.id.toLowerCase() as AccountId }),
  );
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        !item.displayName.trim() ||
        (after !== null && item.id <= after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 10 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
