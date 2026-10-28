import { z } from "zod";

const count = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const month = z.string().regex(/^\d{4}-(?:0[1-9]|1[0-2])$/u);
const facts = z.object({
  workedDays: count,
  absenceDays: count,
  unrecordedDays: count,
  offDays: count,
  unassignedDays: count,
  acceptedMinutes: count,
});
const counts = z.object({
  closedMonths: count,
  employmentMonths: count,
  employments: count,
  adjustedEmploymentMonths: count,
  days: count,
  scheduledDays: count,
  scheduledMinutes: count,
  original: facts,
  current: facts,
});
const closing = z.object({
  periodId: z.uuid(),
  jobId: z.uuid(),
  version: count.min(1),
  closedAt: z.iso.datetime(),
  timezone: z.string().min(1).max(64),
});
export const attendanceClosingReportDto = z.object({
  companyId: z.uuid(),
  from: month,
  until: month,
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("attendance-closings.v1"),
  totals: counts,
  months: z
    .array(z.object({ month, closing: closing.nullable(), counts }))
    .min(1)
    .max(12),
});
export const attendanceClosingEmployeePageDto = z.object({
  companyId: z.uuid(),
  month,
  evaluatedAt: z.iso.datetime(),
  definitionVersion: z.literal("attendance-closings.v1"),
  items: z
    .array(
      z.object({
        employeeId: z.uuid(),
        employeeNumber: z.string().trim().min(1).max(32),
        employeeName: z.string().trim().min(1).max(200),
        closing,
        adjustment: z
          .object({ id: z.uuid(), revision: count.min(1), recordedAt: z.iso.datetime() })
          .nullable(),
        counts,
      }),
    )
    .max(50),
  nextCursor: z.string().max(110).nullable(),
});
export type AttendanceClosingReportDto = z.infer<typeof attendanceClosingReportDto>;
export type AttendanceClosingEmployeePageDto = z.infer<typeof attendanceClosingEmployeePageDto>;
