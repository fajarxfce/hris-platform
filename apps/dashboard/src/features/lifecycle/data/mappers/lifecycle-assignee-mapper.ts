import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { LifecycleAssigneePage } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleAssigneePageDto } from "../models/lifecycle-assignee-dto";

export function toLifecycleAssigneePage(
  dto: LifecycleAssigneePageDto,
  companyId: CompanyId,
  after: string | null,
): LifecycleAssigneePage {
  const items = dto.items.map((item) =>
    Object.freeze({
      id: item.id.toLowerCase() as AccountId,
      companyId,
      displayName: item.displayName,
    }),
  );
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    items.some(
      (item, index) =>
        !item.displayName.trim() ||
        (after !== null && item.id <= after) ||
        (index > 0 && item.id <= (items[index - 1]?.id ?? "")),
    ) ||
    (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.id))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
