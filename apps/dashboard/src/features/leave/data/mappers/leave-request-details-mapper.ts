import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LeaveRequestId } from "../../domain/entities/leave-request";
import type { LeaveRequestDetails } from "../../domain/entities/leave-request-details";
import type { LeaveRequestDetailsDto } from "../models/leave-request-details-dto";
import { toLeaveHistory } from "./leave-history-mapper";
import { toLeaveWorkflow } from "./leave-workflow-mapper";

export function toLeaveRequestDetails(
  dto: LeaveRequestDetailsDto,
  companyId: CompanyId,
  historyAfter: string | null,
): LeaveRequestDetails {
  let previous = "";
  const days = dto.days.map((day) => {
    const start = utcInstantMicroseconds(day.startsAt);
    const end = utcInstantMicroseconds(day.endsAt);
    if (
      !isCalendarDate(day.workDate) ||
      day.workDate <= previous ||
      start === null ||
      end === null ||
      start >= end ||
      day.chargedMinutes > day.plannedMinutes ||
      day.chargedDays !== (day.portion === "FULL" ? "1" : "0.5")
    )
      throw new InvalidHttpResponseError();
    previous = day.workDate;
    return Object.freeze({ ...day, shiftId: day.shiftId.toLowerCase() });
  });
  if (
    utcInstantMicroseconds(dto.submittedAt) === null ||
    days.reduce((sum, day) => sum + Number(day.chargedDays), 0) !== Number(dto.chargedDays) ||
    new Set(dto.availableActions).size !== dto.availableActions.length ||
    new Set(dto.policy.allowedContracts).size !== dto.policy.allowedContracts.length ||
    new Set(dto.attachments.map((item) => item.revisionId.toLowerCase())).size !==
      dto.attachments.length ||
    (dto.status === "CANCELLATION_PENDING" && dto.cancellation === null)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase() as LeaveRequestId,
    companyId,
    employeeId: dto.employeeId.toLowerCase(),
    ownerAccountId: (dto.ownerAccountId?.toLowerCase() as AccountId | undefined) ?? null,
    authorId: dto.authorId.toLowerCase() as AccountId,
    days: Object.freeze(days),
    policy: Object.freeze({
      ...dto.policy,
      typeId: dto.policy.typeId.toLowerCase(),
      allowedContracts: Object.freeze([...dto.policy.allowedContracts]),
    }),
    approval: toLeaveWorkflow(dto.approval),
    cancellation: dto.cancellation === null ? null : toLeaveWorkflow(dto.cancellation),
    history: toLeaveHistory(dto.history, dto.version, historyAfter),
    availableActions: Object.freeze([...dto.availableActions]),
    attachments: Object.freeze(
      dto.attachments.map((item) =>
        Object.freeze({
          ...item,
          documentId: item.documentId.toLowerCase(),
          revisionId: item.revisionId.toLowerCase(),
        }),
      ),
    ),
  });
}
