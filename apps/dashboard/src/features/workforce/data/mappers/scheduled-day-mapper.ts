import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { ScheduledDay } from "../../domain/entities/scheduled-day";
import type { ShiftId } from "../../domain/entities/shift-definition";
import type { ShiftSnapshot } from "../../domain/entities/shift-snapshot";
import type { ScheduledDayDto, ShiftSnapshotDto } from "../models/calendar-dto";
import { toShiftDetails } from "./shift-mapper";

export function toShiftSnapshot(dto: ShiftSnapshotDto): ShiftSnapshot {
  return Object.freeze({
    id: dto.id.toLowerCase() as ShiftId,
    revision: dto.revision,
    details: toShiftDetails(dto.details),
  });
}
export function toScheduledDay(
  dto: ScheduledDayDto,
  workDate: string,
  scheduleVersion?: number | null,
): ScheduledDay {
  if (
    dto.workDate !== workDate ||
    (scheduleVersion !== undefined &&
      dto.origin === "PATTERN" &&
      (scheduleVersion === null ||
        dto.originVersion === null ||
        dto.originVersion > scheduleVersion))
  )
    throw new InvalidHttpResponseError();
  if (dto.kind === "UNASSIGNED") {
    if (
      dto.origin !== null ||
      dto.originVersion !== null ||
      dto.shift !== null ||
      dto.startsAt !== null ||
      dto.endsAt !== null ||
      dto.holidayId !== null ||
      dto.plannedMinutes !== 0
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({ kind: "UNASSIGNED", workDate });
  }
  if (dto.origin === null || dto.originVersion === null) throw new InvalidHttpResponseError();
  if (dto.kind === "OFF") {
    if (
      dto.shift !== null ||
      dto.startsAt !== null ||
      dto.endsAt !== null ||
      dto.plannedMinutes !== 0 ||
      (dto.origin === "HOLIDAY") !== (dto.holidayId !== null)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      kind: "OFF",
      workDate,
      origin: dto.origin,
      originVersion: dto.originVersion,
      holidayId: dto.holidayId?.toLowerCase() ?? null,
    });
  }
  if (
    dto.origin === "HOLIDAY" ||
    dto.holidayId !== null ||
    dto.shift === null ||
    dto.startsAt === null ||
    dto.endsAt === null
  )
    throw new InvalidHttpResponseError();
  const start = utcInstantMicroseconds(dto.startsAt);
  const end = utcInstantMicroseconds(dto.endsAt);
  if (
    start === null ||
    end === null ||
    end <= start ||
    end - start > 1_620n * 60_000_000n ||
    end - start !== BigInt(dto.plannedMinutes + dto.shift.details.breakMinutes) * 60_000_000n
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    kind: "WORK",
    workDate,
    origin: dto.origin,
    originVersion: dto.originVersion,
    shift: toShiftSnapshot(dto.shift),
    startsAt: dto.startsAt,
    endsAt: dto.endsAt,
    plannedMinutes: dto.plannedMinutes,
  });
}
