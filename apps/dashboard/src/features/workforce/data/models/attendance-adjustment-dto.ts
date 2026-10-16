import { z } from "zod";
import { attendanceDayDto } from "./attendance-dto";
import { workforceEmployeeDto } from "./calendar-dto";
import { workPeriodDto } from "./work-period-dto";
import { closedWorkDayDto, workPeriodSnapshotDto } from "./work-period-snapshot-dto";

export const attendanceAdjustmentSummaryDto = z.object({
  id: z.uuid(),
  revision: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  correctionId: z.uuid(),
  actorId: z.uuid(),
  recordedAt: z.string().min(20).max(40),
  reason: z.string().min(1).max(1000),
});
export const attendanceAdjustmentReviewDto = z.object({
  employee: workforceEmployeeDto,
  period: workPeriodDto,
  original: closedWorkDayDto,
  current: closedWorkDayDto,
  attendance: attendanceDayDto,
  adjustmentRevision: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
  revision: z.string().regex(/^[0-9a-f]{64}$/u),
  canCorrect: z.boolean(),
  canReviewEvidence: z.boolean(),
});
export const attendanceAdjustmentPageDto = z.object({
  items: z.array(attendanceAdjustmentSummaryDto).max(25),
  nextCursor: z.string().nullable(),
});
export const attendanceAdjustmentDetailsDto = z.object({
  summary: attendanceAdjustmentSummaryDto,
  snapshot: workPeriodSnapshotDto,
});
export type AttendanceAdjustmentReviewDto = z.infer<typeof attendanceAdjustmentReviewDto>;
export type AttendanceAdjustmentSummaryDto = z.infer<typeof attendanceAdjustmentSummaryDto>;
export type AttendanceAdjustmentPageDto = z.infer<typeof attendanceAdjustmentPageDto>;
export type AttendanceAdjustmentDetailsDto = z.infer<typeof attendanceAdjustmentDetailsDto>;
export type AttendanceAdjustmentCommandDto = Readonly<{
  workDate: string;
  reviewRevision: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  reason: string;
  decisions: readonly Readonly<{
    eventId: string;
    expectedVersion: number;
    decision: "ACCEPT" | "REJECT";
  }>[];
}>;
