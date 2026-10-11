import { z } from "zod";
import { shiftDetailsDto } from "./shift-dto";

const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/u);
const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const weekday = z.enum([
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
]);
export const workforceEmployeeDto = z.object({
  id: z.uuid(),
  employeeNumber: z.string().min(2).max(32),
  legalName: z.string().min(1).max(200),
  status: z.enum(["PROBATION", "ACTIVE", "SUSPENDED", "ENDED"]),
  version,
});
export const workforceEmployeePageDto = z.object({
  items: z.array(workforceEmployeeDto).max(50),
  nextCursor: z.string().min(2).max(32).nullable(),
});
export const shiftSnapshotDto = z.object({
  id: z.uuid(),
  revision: version,
  details: shiftDetailsDto,
});
export const scheduledDayDto = z.object({
  workDate: date,
  kind: z.enum(["WORK", "OFF", "UNASSIGNED"]),
  shift: shiftSnapshotDto.nullable(),
  startsAt: z.string().max(40).nullable(),
  endsAt: z.string().max(40).nullable(),
  plannedMinutes: z.number().int().min(0).max(1620),
  origin: z.enum(["PATTERN", "ROSTER", "HOLIDAY"]).nullable(),
  originVersion: version.nullable(),
  holidayId: z.uuid().nullable(),
});
export const calendarReviewDto = z.object({
  employee: workforceEmployeeDto,
  from: date,
  until: date,
  calendar: z.object({
    scheduleVersion: version.nullable(),
    days: z.array(scheduledDayDto).min(1).max(62),
  }),
  assignmentAtStart: z
    .object({
      effectiveFrom: date,
      revision: version,
      days: z.partialRecord(weekday, shiftSnapshotDto),
    })
    .nullable(),
});
export const calendarReceiptDto = z.object({ id: z.uuid(), version });
export type WorkforceEmployeeDto = z.infer<typeof workforceEmployeeDto>;
export type WorkforceEmployeePageDto = z.infer<typeof workforceEmployeePageDto>;
export type ShiftSnapshotDto = z.infer<typeof shiftSnapshotDto>;
export type ScheduledDayDto = z.infer<typeof scheduledDayDto>;
export type CalendarReviewDto = z.infer<typeof calendarReviewDto>;
export type CalendarReceiptDto = z.infer<typeof calendarReceiptDto>;
export type CalendarRangeDto = Readonly<{ from: string; until: string }>;
export type WorkforceEmployeeSearchDto = Readonly<{
  asOf: string;
  query: string;
  after: string | null;
}>;
export type ShiftReferenceDto = Readonly<{ id: string; version: number }>;
export type WeeklyScheduleChangeDto = Readonly<{
  effectiveFrom: string;
  days: Readonly<Partial<Record<z.infer<typeof weekday>, ShiftReferenceDto>>>;
  expectedVersion: number | null;
  reason: string;
}>;
export type RosterDayChangeDto = Readonly<{
  shift: ShiftReferenceDto | null;
  expectedVersion: number | null;
  reason: string;
}>;
