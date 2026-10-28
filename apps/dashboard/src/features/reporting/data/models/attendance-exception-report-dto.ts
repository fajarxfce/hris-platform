import { z } from "zod";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const attendanceExceptionCountsDto = z.object({
  observedDays: count,
  affectedDays: count,
  affectedEmployments: count,
  pendingDays: count,
  incompleteDays: count,
  pendingEvents: count,
});
export const attendanceExceptionReportDto = z.object({
  companyId: z.uuid(),
  from: z.iso.date(),
  until: z.iso.date(),
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("attendance-exceptions.v1"),
  totals: attendanceExceptionCountsDto,
  days: z
    .array(z.object({ date: z.iso.date(), counts: attendanceExceptionCountsDto }))
    .min(1)
    .max(31),
});
export type AttendanceExceptionReportDto = z.infer<typeof attendanceExceptionReportDto>;
export type AttendanceExceptionCountsDto = z.infer<typeof attendanceExceptionCountsDto>;
