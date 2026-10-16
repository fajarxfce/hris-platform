import { z } from "zod";
import { type ShiftReferenceDto, scheduledDayDto, workforceEmployeeDto } from "./calendar-dto";

const mode = z.enum(["SHIFT", "DAY_OFF", "CALENDAR"]);
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/u);
const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const rosterBulkDayDto = z.object({
  workDate: date,
  expectedVersion: version.nullable(),
  overrideActive: z.boolean(),
  before: scheduledDayDto,
  after: scheduledDayDto,
  changed: z.boolean(),
});
export const rosterBulkReviewDto = z.object({
  employeeIds: z.array(z.uuid()).min(1).max(50),
  dates: z.array(date).min(1).max(31),
  mode,
  shiftId: z.uuid().nullable(),
  shiftVersion: version.nullable(),
  employees: z
    .array(
      z.object({ employee: workforceEmployeeDto, days: z.array(rosterBulkDayDto).min(1).max(31) }),
    )
    .min(1)
    .max(50),
  revision: z.string().regex(/^[0-9a-f]{64}$/u),
  changeCount: z.number().int().min(0).max(200),
});
export type RosterBulkReviewDto = z.infer<typeof rosterBulkReviewDto>;
export type RosterBulkSelectionDto = Readonly<{
  employeeIds: readonly string[];
  dates: readonly string[];
  mode: z.infer<typeof mode>;
  shift: ShiftReferenceDto | null;
}>;
export type RosterBulkApplyDto = Readonly<{
  selection: RosterBulkSelectionDto;
  reviewRevision: string;
  reason: string;
}>;
