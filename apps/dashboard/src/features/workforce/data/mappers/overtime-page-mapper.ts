import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimePage } from "../../domain/entities/overtime-page";
import type { OvertimeSearch } from "../../domain/entities/overtime-search";
import type { OvertimePageDto } from "../models/overtime-request-dto";
import { toOvertimeRequest } from "./overtime-request-mapper";

export function toOvertimePage(
  dto: OvertimePageDto,
  companyId: CompanyId,
  search: OvertimeSearch,
): OvertimePage {
  let previous = search.after ?? "";
  const items = dto.items.map((row) => {
    const item = toOvertimeRequest(row, companyId);
    if (
      item.id <= previous ||
      item.workDate < search.from ||
      item.workDate > search.until ||
      (search.employeeId !== null && item.employeeId !== search.employeeId) ||
      (search.status !== null && item.status !== search.status)
    )
      throw new InvalidHttpResponseError();
    previous = item.id;
    return item;
  });
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (nextCursor !== null && (items.length !== 50 || nextCursor !== items.at(-1)?.id))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId,
    search: Object.freeze({ ...search }),
    items: Object.freeze(items),
    nextCursor,
  });
}
