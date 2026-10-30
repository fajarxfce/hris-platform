import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { ApprovalHistory } from "../../domain/entities/approval-history";
import type { ApprovalId } from "../../domain/entities/approval-request";
import type { ApprovalHistoryDto } from "../models/approval-history-dto";
import { toApprovalRequest } from "./approval-request-mapper";
export function toApprovalHistory(
  dto: ApprovalHistoryDto,
  company: CompanyId,
  id: ApprovalId,
  after: number | null,
  expectedVersion: number | null,
): ApprovalHistory {
  const request = toApprovalRequest(dto.request, company);
  if (request.id !== id || (expectedVersion !== null && request.version !== expectedVersion))
    throw new InvalidHttpResponseError();
  const decisions = dto.decisions.map((entry, index) => {
    if (
      entry.step >= request.stages.length ||
      (index > 0 && entry.step <= (dto.decisions[index - 1]?.step ?? -1))
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...entry,
      actorId: entry.actorId.toLowerCase() as AccountId,
      decidingFor: entry.decidingFor.toLowerCase() as AccountId,
    });
  });
  const items = dto.reassignments.items.map((entry, index) => {
    const assignees = entry.assignees.map((account) => account.toLowerCase() as AccountId);
    if (
      new Set(assignees).size !== assignees.length ||
      entry.step >= request.stages.length ||
      entry.revision > request.version ||
      entry.revision <= (after ?? 0) ||
      (index > 0 && entry.revision <= (dto.reassignments.items[index - 1]?.revision ?? 0))
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...entry,
      actorId: entry.actorId.toLowerCase() as AccountId,
      assignees: Object.freeze(assignees),
    });
  });
  const next = dto.reassignments.nextCursor;
  if (
    next !== null &&
    (items.length !== 50 ||
      !Number.isSafeInteger(Number(next)) ||
      next !== String(items.at(-1)?.revision))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    request,
    decisions: Object.freeze(decisions),
    reassignments: Object.freeze({ items: Object.freeze(items), nextCursor: next }),
  });
}
