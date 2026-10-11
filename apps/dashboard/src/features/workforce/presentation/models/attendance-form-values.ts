import { companyDateTimeInput } from "../../../../core/presentation/dates/company-date-time";
import type { AttendanceDay } from "../../domain/entities/attendance-day";

export type AttendanceCorrectionValues = {
  absence: boolean;
  clockIn: string;
  clockOut: string;
  breakMinutes: string;
  reason: string;
};
export function attendanceCorrectionValues(
  day: AttendanceDay | null,
  timezone: string,
): AttendanceCorrectionValues {
  const clockIn = day?.entries.find(
    (entry) => entry.kind === "CLOCK_IN" && entry.status === "ACCEPTED",
  );
  const start = day?.correction ? day.correction.clockIn : clockIn?.capturedAt;
  const end = day?.correction
    ? day.correction.clockOut
    : day?.entries.find((entry) => entry.kind === "CLOCK_OUT" && entry.status === "ACCEPTED")
        ?.capturedAt;
  return {
    absence: day?.correction?.clockIn === null,
    clockIn: start ? companyDateTimeInput(start, timezone).slice(0, 16) : "",
    clockOut: end ? companyDateTimeInput(end, timezone).slice(0, 16) : "",
    breakMinutes: String(
      day?.correction?.breakMinutes ??
        (clockIn?.schedule.kind === "WORK" ? clockIn.schedule.shift.details.breakMinutes : 0),
    ),
    reason: "",
  };
}
