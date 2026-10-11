import { z } from "zod";
import { scheduledDayDto } from "./calendar-dto";

const instant = z.string().min(20).max(40);
const version = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const reason = z.string().min(1).max(1000);
export const attendanceIssues = [
  "OFFLINE",
  "PERIOD_LOCKED",
  "CORRECTED_DAY",
  "UNVERIFIED_CAPTURE",
  "WINDOW_EXPIRED",
  "LOCATION_REQUIRED",
  "LOW_ACCURACY",
  "OUTSIDE_FENCE",
  "MOCK_LOCATION",
  "UNSCHEDULED",
  "OUTSIDE_SHIFT_WINDOW",
  "SEQUENCE_REVIEW",
] as const;
const review = z.object({
  actorId: z.uuid(),
  decision: z.enum(["ACCEPT", "REJECT"]),
  reviewedAt: instant,
  reason,
});
export const attendanceCorrectionDto = z.object({
  id: z.uuid(),
  clockIn: instant.nullable(),
  clockOut: instant.nullable(),
  breakMinutes: z.number().int().min(0).max(1439),
  actorId: z.uuid(),
  recordedAt: instant,
  reason,
  version,
});
export const attendanceEntryDto = z.object({
  id: z.uuid(),
  kind: z.enum(["CLOCK_IN", "CLOCK_OUT"]),
  capturedAt: instant,
  receivedAt: instant,
  offline: z.boolean(),
  location: z
    .object({
      latitude: z.number().min(-90).max(90),
      longitude: z.number().min(-180).max(180),
      accuracyMeters: z.number().min(0).max(100000),
      mocked: z.boolean(),
    })
    .nullable(),
  schedule: scheduledDayDto,
  status: z.enum(["ACCEPTED", "PENDING", "REJECTED"]),
  initialStatus: z.enum(["ACCEPTED", "PENDING"]),
  issues: z.array(z.enum(attendanceIssues)).max(12),
  review: review.nullable(),
  version,
});
export const attendanceDayDto = z.object({
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  entries: z.array(attendanceEntryDto).max(32),
  acceptedMinutes: z.number().int().min(0).max(1440),
  pendingCount: z.number().int().min(0).max(32),
  incomplete: z.boolean(),
  correction: attendanceCorrectionDto.nullable(),
});
export const attendanceDaysDto = z.array(attendanceDayDto).min(1).max(31);
export const attendanceReceiptDto = z.object({ id: z.uuid(), version });
export type AttendanceDayDto = z.infer<typeof attendanceDayDto>;
export type AttendanceEntryDto = z.infer<typeof attendanceEntryDto>;
export type AttendanceCorrectionDto = z.infer<typeof attendanceCorrectionDto>;
export type AttendanceReceiptDto = z.infer<typeof attendanceReceiptDto>;
export type AttendanceReviewDto = Readonly<{
  version: number;
  decision: "ACCEPT" | "REJECT";
  reason: string;
}>;
export type AttendanceCorrectionChangeDto = Readonly<{
  workDate: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  expectedVersion: number | null;
  reason: string;
}>;
