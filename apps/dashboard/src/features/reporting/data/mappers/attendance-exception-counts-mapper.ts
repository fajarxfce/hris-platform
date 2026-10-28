import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { AttendanceExceptionCounts } from "../../domain/entities/attendance-exception-counts";
import type { AttendanceExceptionCountsDto } from "../models/attendance-exception-report-dto";

export function toAttendanceExceptionCounts(
  dto: AttendanceExceptionCountsDto,
): AttendanceExceptionCounts {
  if (
    Object.values(dto).some((count) => !Number.isSafeInteger(count) || count < 0) ||
    dto.affectedDays > dto.observedDays ||
    dto.affectedEmployments > dto.affectedDays ||
    (dto.affectedDays === 0) !== (dto.affectedEmployments === 0) ||
    dto.pendingDays > dto.affectedDays ||
    dto.incompleteDays > dto.affectedDays ||
    !Number.isSafeInteger(dto.pendingDays + dto.incompleteDays) ||
    dto.affectedDays > dto.pendingDays + dto.incompleteDays ||
    dto.pendingEvents < dto.pendingDays ||
    (dto.pendingDays === 0) !== (dto.pendingEvents === 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}
