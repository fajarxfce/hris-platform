import { toAttendanceCorrectionPage } from "../data/mappers/attendance-correction-page-mapper";
import type { AttendanceCorrectionPageDto } from "../data/models/attendance-correction-page-dto";
import type { AttendanceCorrectionDto } from "../data/models/attendance-dto";
import type { AttendanceCorrectionHistory } from "../domain/entities/attendance-correction-history";
import { attendanceDate, company, employeeId, identifier } from "./attendance-fixture";
import { calendarFixture } from "./calendar-fixture";

export function attendanceCorrectionDtoFixture(version = 0): AttendanceCorrectionDto {
  return {
    id: identifier(3000 + version),
    clockIn: version === 0 ? null : `${attendanceDate}T15:15:00Z`,
    clockOut: version === 0 ? null : "2026-10-12T23:00:00Z",
    breakMinutes: version === 0 ? 0 : 30,
    actorId: identifier(1),
    recordedAt: "2026-10-13T01:00:00Z",
    reason: version === 0 ? "Verified absence" : `Attendance correction ${version}`,
    version,
  };
}
export function attendanceCorrectionPageDtoFixture(
  after: string | null = null,
  count = 53,
): AttendanceCorrectionPageDto {
  const start = Math.min(after === null ? count : Number(after), count);
  const items = Array.from({ length: Math.min(start, 50) }, (_, index) =>
    attendanceCorrectionDtoFixture(start - index - 1),
  );
  return { items, nextCursor: start > 50 ? String(start - 50) : null };
}
export function attendanceCorrectionHistoryFixture(
  after: string | null = null,
): AttendanceCorrectionHistory {
  return Object.freeze({
    ...toAttendanceCorrectionPage(
      attendanceCorrectionPageDtoFixture(after),
      company,
      employeeId,
      attendanceDate,
      after,
    ),
    employee: calendarFixture().employee,
  });
}
