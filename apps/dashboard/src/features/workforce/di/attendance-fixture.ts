import { toEmployeeAttendance } from "../data/mappers/attendance-mapper";
import type { AttendanceDayDto } from "../data/models/attendance-dto";
import type { CalendarRange } from "../domain/entities/calendar-range";
import {
  calendarDtoFixture,
  calendarFixture,
  company,
  employeeId,
  identifier,
} from "./calendar-fixture";

export { company, employeeId, identifier };
export const attendanceAccess = {
  companyId: company,
  permissions: ["workforce.read", "attendance.verify", "attendance.correct"],
};
export const attendanceDate = "2026-10-12";
export const eventId = identifier(801);
export function attendanceDtoFixture(
  range: CalendarRange = { from: attendanceDate, until: attendanceDate },
): AttendanceDayDto[] {
  return calendarDtoFixture(range).calendar.days.map((schedule, index) => ({
    workDate: schedule.workDate,
    acceptedMinutes: 0,
    pendingCount: 1,
    incomplete: false,
    correction: null,
    entries: [
      {
        id: identifier(801 + index),
        kind: "CLOCK_IN",
        capturedAt: schedule.startsAt ?? "2026-10-12T15:00:00Z",
        receivedAt: schedule.startsAt ?? "2026-10-12T15:00:00Z",
        offline: true,
        location: { latitude: -6.2, longitude: 106.8, accuracyMeters: 15, mocked: false },
        schedule,
        status: "PENDING",
        initialStatus: "PENDING",
        issues: ["OFFLINE"],
        review: null,
        version: 0,
      },
    ],
  }));
}
export function attendanceFixture(
  range: CalendarRange = { from: attendanceDate, until: attendanceDate },
) {
  return Object.freeze({
    ...toEmployeeAttendance(attendanceDtoFixture(range), company, employeeId, range),
    employee: calendarFixture(range).employee,
  });
}
