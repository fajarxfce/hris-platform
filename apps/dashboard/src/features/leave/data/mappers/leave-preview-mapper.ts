import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { LeaveRequestPreview } from "../../domain/entities/leave-request-preview";
import type { LeaveRequestSelection } from "../../domain/entities/leave-request-selection";
import type { LeaveRequestPreviewDto } from "../models/leave-request-preview-dto";

/** Validates the response's scope, selected-date partition and exact half-day projections. */
export function toLeaveRequestPreview(
  dto: LeaveRequestPreviewDto,
  companyId: CompanyId,
  selection: LeaveRequestSelection,
): LeaveRequestPreview {
  if (
    dto.employeeId.toLowerCase() !== selection.employeeId ||
    dto.policy.typeId.toLowerCase() !== selection.typeId ||
    utcInstantMicroseconds(dto.evaluatedAt) === null ||
    new Set(dto.policy.allowedContracts).size !== dto.policy.allowedContracts.length
  )
    throw new InvalidHttpResponseError();
  try {
    new Intl.DateTimeFormat("en", { timeZone: dto.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  const requested = new Map(selection.days.map((day) => [day.workDate, day.portion]));
  const annual = new Map<number, number>();
  let previous = "";
  const days = dto.days.map((day) => {
    const start = utcInstantMicroseconds(day.startsAt),
      end = utcInstantMicroseconds(day.endsAt);
    const minutes =
      day.portion === "FULL"
        ? day.plannedMinutes
        : day.portion === "FIRST_HALF"
          ? Math.ceil(day.plannedMinutes / 2)
          : Math.floor(day.plannedMinutes / 2);
    if (
      !isCalendarDate(day.workDate) ||
      day.workDate <= previous ||
      requested.get(day.workDate) !== day.portion ||
      start === null ||
      end === null ||
      start >= end ||
      day.chargedMinutes !== minutes ||
      day.chargedDays !== (day.portion === "FULL" ? "1" : "0.5")
    )
      throw new InvalidHttpResponseError();
    previous = day.workDate;
    requested.delete(day.workDate);
    const year = Number(day.workDate.slice(0, 4));
    annual.set(year, (annual.get(year) ?? 0) + Number(day.chargedDays));
    return Object.freeze({ ...day, shiftId: day.shiftId.toLowerCase() });
  });
  previous = "";
  for (const excluded of dto.excludedDates) {
    if (!isCalendarDate(excluded) || excluded <= previous || !requested.delete(excluded))
      throw new InvalidHttpResponseError();
    previous = excluded;
  }
  if (
    requested.size > 0 ||
    days.reduce((sum, day) => sum + Number(day.chargedDays), 0) !== Number(dto.chargedDays) ||
    dto.balances.length !== annual.size ||
    new Set(dto.balances.map((balance) => balance.year)).size !== dto.balances.length
  )
    throw new InvalidHttpResponseError();
  let previousYear = 0;
  const balances = dto.balances.map((balance) => {
    if (
      balance.year <= previousYear ||
      annual.get(balance.year) !== Number(balance.requestedDays) ||
      Number(balance.availableDays) - Number(balance.requestedDays) !==
        Number(balance.remainingDays)
    )
      throw new InvalidHttpResponseError();
    previousYear = balance.year;
    return Object.freeze({ ...balance });
  });
  return Object.freeze({
    ...dto,
    companyId,
    employeeId: dto.employeeId.toLowerCase(),
    policy: Object.freeze({
      ...dto.policy,
      typeId: dto.policy.typeId.toLowerCase(),
      allowedContracts: Object.freeze([...dto.policy.allowedContracts]),
    }),
    days: Object.freeze(days),
    excludedDates: Object.freeze([...dto.excludedDates]),
    balances: Object.freeze(balances),
  });
}
