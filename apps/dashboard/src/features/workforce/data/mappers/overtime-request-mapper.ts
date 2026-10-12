import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimeRequest, OvertimeRequestId } from "../../domain/entities/overtime-request";
import type { OvertimeRequestDto } from "../models/overtime-request-dto";
import { toOvertimeInterval } from "./overtime-interval-mapper";
import { toScheduledDay } from "./scheduled-day-mapper";

export function toOvertimeRequest(dto: OvertimeRequestDto, companyId: CompanyId): OvertimeRequest {
  const schedule = toScheduledDay(dto.schedule, dto.workDate);
  const requested = toOvertimeInterval(dto.requested);
  const actual = dto.actual === null ? null : toOvertimeInterval(dto.actual);
  const terminal = ["APPROVED", "REJECTED", "WITHDRAWN"].includes(dto.status);
  if (
    !isCalendarDate(dto.workDate) ||
    dto.workDate < "2000-01-01" ||
    dto.workDate > "2100-12-31" ||
    schedule.kind === "UNASSIGNED" ||
    !dto.employeeName.trim() ||
    !dto.employeeNumber.trim() ||
    !dto.reason.trim() ||
    [dto.createdAt, dto.submittedAt, dto.decidedAt].some(
      (value) => value !== null && utcInstantMicroseconds(value) === null,
    ) ||
    (actual === null) !== (dto.submittedBy === null) ||
    (actual === null) !== (dto.submittedAt === null) ||
    (actual === null) !== (dto.approvalId === null) ||
    terminal !== (dto.decidedAt !== null) ||
    (dto.status === "PLANNED" && (actual !== null || dto.version !== 0)) ||
    (dto.status !== "PLANNED" && dto.version === 0) ||
    (["PENDING", "APPROVED", "REJECTED"].includes(dto.status) && actual === null) ||
    dto.approvedMinutes !== (dto.status === "APPROVED" ? actual?.workedMinutes : 0)
  )
    throw new InvalidHttpResponseError();
  if (actual !== null) {
    const start = utcInstantMicroseconds(actual.startsAt),
      end = utcInstantMicroseconds(actual.endsAt);
    const requestedStart = utcInstantMicroseconds(requested.startsAt),
      requestedEnd = utcInstantMicroseconds(requested.endsAt);
    if (
      start === null ||
      end === null ||
      requestedStart === null ||
      requestedEnd === null ||
      start < requestedStart ||
      end > requestedEnd ||
      actual.workedMinutes > requested.workedMinutes
    )
      throw new InvalidHttpResponseError();
  }
  try {
    new Intl.DateTimeFormat("en", { timeZone: dto.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    ...dto,
    companyId,
    id: dto.id.toLowerCase() as OvertimeRequestId,
    employeeId: dto.employeeId.toLowerCase() as EmployeeId,
    authorId: dto.authorId.toLowerCase() as AccountId,
    requesterAccountId: (dto.requesterAccountId?.toLowerCase() as AccountId | undefined) ?? null,
    submittedBy: (dto.submittedBy?.toLowerCase() as AccountId | undefined) ?? null,
    approvalId: dto.approvalId?.toLowerCase() ?? null,
    schedule,
    requested,
    actual,
  });
}
