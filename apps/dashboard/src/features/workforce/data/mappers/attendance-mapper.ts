import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrection } from "../../domain/entities/attendance-correction";
import type { AttendanceEntry } from "../../domain/entities/attendance-entry";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { EmployeeAttendance } from "../../domain/entities/employee-attendance";
import type {
  AttendanceCorrectionDto,
  AttendanceDayDto,
  AttendanceEntryDto,
  AttendanceReceiptDto,
} from "../models/attendance-dto";
import { toScheduledDay } from "./scheduled-day-mapper";

export function toAttendanceEntry(dto: AttendanceEntryDto, date: string): AttendanceEntry {
  if (
    utcInstantMicroseconds(dto.capturedAt) === null ||
    utcInstantMicroseconds(dto.receivedAt) === null ||
    new Set(dto.issues).size !== dto.issues.length ||
    (dto.review === null && (dto.status !== dto.initialStatus || dto.version !== 0)) ||
    (dto.review !== null &&
      (dto.initialStatus !== "PENDING" ||
        dto.version !== 1 ||
        dto.status !== (dto.review.decision === "ACCEPT" ? "ACCEPTED" : "REJECTED") ||
        utcInstantMicroseconds(dto.review.reviewedAt) === null))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    id: dto.id.toLowerCase(),
    issues: Object.freeze([...dto.issues]),
    location: dto.location ? Object.freeze({ ...dto.location }) : null,
    review: dto.review
      ? Object.freeze({ ...dto.review, actorId: dto.review.actorId.toLowerCase() })
      : null,
    schedule: toScheduledDay(dto.schedule, date),
  });
}
export function toAttendanceCorrection(dto: AttendanceCorrectionDto): AttendanceCorrection {
  if (
    utcInstantMicroseconds(dto.recordedAt) === null ||
    (dto.clockIn === null) !== (dto.clockOut === null)
  )
    throw new InvalidHttpResponseError();
  if (dto.clockIn === null || dto.clockOut === null) {
    if (dto.breakMinutes !== 0) throw new InvalidHttpResponseError();
  } else {
    const start = utcInstantMicroseconds(dto.clockIn),
      end = utcInstantMicroseconds(dto.clockOut);
    if (
      start === null ||
      end === null ||
      start % 60_000_000n !== 0n ||
      end % 60_000_000n !== 0n ||
      end <= start ||
      end - start > 86_400_000_000n ||
      BigInt(dto.breakMinutes) * 60_000_000n >= end - start
    )
      throw new InvalidHttpResponseError();
  }
  return Object.freeze({ ...dto, id: dto.id.toLowerCase(), actorId: dto.actorId.toLowerCase() });
}
export function toEmployeeAttendance(
  dto: readonly AttendanceDayDto[],
  company: CompanyId,
  employee: EmployeeId,
  range: CalendarRange,
): EmployeeAttendance {
  const start = Date.parse(`${range.from}T00:00:00Z`);
  const count = (Date.parse(`${range.until}T00:00:00Z`) - start) / 86_400_000 + 1;
  if (dto.length !== count) throw new InvalidHttpResponseError();
  const identifiers = new Set<string>();
  const days = dto.map((day, index) => {
    if (day.workDate !== new Date(start + index * 86_400_000).toISOString().slice(0, 10))
      throw new InvalidHttpResponseError();
    const entries = day.entries.map((entry) => {
      const mapped = toAttendanceEntry(entry, day.workDate);
      if (identifiers.has(mapped.id)) throw new InvalidHttpResponseError();
      identifiers.add(mapped.id);
      return mapped;
    });
    if (day.pendingCount !== entries.filter((entry) => entry.status === "PENDING").length)
      throw new InvalidHttpResponseError();
    return Object.freeze({
      ...day,
      entries: Object.freeze(entries),
      correction: day.correction ? toAttendanceCorrection(day.correction) : null,
    });
  });
  return Object.freeze({
    companyId: company,
    employeeId: employee,
    range: Object.freeze({ ...range }),
    days: Object.freeze(days),
  });
}
export function toAttendanceReceipt(dto: AttendanceReceiptDto, version: number, id?: string) {
  if (dto.version !== version || (id !== undefined && dto.id.toLowerCase() !== id.toLowerCase()))
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
