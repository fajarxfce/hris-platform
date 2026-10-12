import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AccountId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OvertimeHistory } from "../../domain/entities/overtime-history";
import type { OvertimeRequest } from "../../domain/entities/overtime-request";
import type { OvertimeHistoryDto } from "../models/overtime-details-dto";
import { toOvertimeInterval } from "./overtime-interval-mapper";

export function toOvertimeHistory(
  dto: OvertimeHistoryDto,
  request: OvertimeRequest,
  after: string | null,
): OvertimeHistory {
  let previous = after === null ? Infinity : Number(after);
  const items = dto.items.map((row) => {
    const actual = row.actual === null ? null : toOvertimeInterval(row.actual);
    if (
      row.revision >= previous ||
      row.revision > request.version ||
      utcInstantMicroseconds(row.recordedAt) === null ||
      (row.kind === "PLANNED") !== (row.revision === 0) ||
      (row.kind === "PLANNED" && (row.status !== "PLANNED" || actual !== null)) ||
      (row.kind === "ACTUAL_SUBMITTED" && (row.status !== "PENDING" || row.revision !== 1)) ||
      (row.kind === "DECIDED" &&
        (!["PENDING", "APPROVED", "REJECTED"].includes(row.status) || row.revision < 2)) ||
      (row.kind === "WITHDRAWN" && row.status !== "WITHDRAWN") ||
      (actual === null) !== (row.approvalId === null) ||
      (["PENDING", "APPROVED", "REJECTED"].includes(row.status) && actual === null) ||
      row.approvedMinutes !== (row.status === "APPROVED" ? actual?.workedMinutes : 0) ||
      (row.kind !== "DECIDED" && !row.reason.trim())
    )
      throw new InvalidHttpResponseError();
    if (
      actual !== null &&
      (request.actual === null ||
        utcInstantMicroseconds(actual.startsAt) !==
          utcInstantMicroseconds(request.actual.startsAt) ||
        utcInstantMicroseconds(actual.endsAt) !== utcInstantMicroseconds(request.actual.endsAt) ||
        actual.breakMinutes !== request.actual.breakMinutes ||
        row.approvalId?.toLowerCase() !== request.approvalId)
    )
      throw new InvalidHttpResponseError();
    previous = row.revision;
    return Object.freeze({
      ...row,
      actual,
      actorId: row.actorId.toLowerCase() as AccountId,
      approvalId: row.approvalId?.toLowerCase() ?? null,
    });
  });
  const latest = items[0];
  if (
    after === null &&
    (!latest ||
      latest.revision !== request.version ||
      latest.status !== request.status ||
      latest.approvedMinutes !== request.approvedMinutes ||
      latest.approvalId !== request.approvalId)
  )
    throw new InvalidHttpResponseError();
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 ||
      dto.nextCursor !== String(items.at(-1)?.revision) ||
      Number(dto.nextCursor) === 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
