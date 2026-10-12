import { z } from "zod";

export const attendanceExceptionDto = z.object({
  employeeId: z.uuid(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  pendingCount: z.number().int().min(0).max(32),
  incomplete: z.boolean(),
  correctionVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER).nullable(),
  lastReceivedAt: z.string().min(20).max(40),
});
export const attendanceExceptionPageDto = z.object({
  items: z.array(attendanceExceptionDto).max(50),
  nextCursor: z.string().min(1).max(160).nullable(),
});
export type AttendanceExceptionDto = z.infer<typeof attendanceExceptionDto>;
export type AttendanceExceptionPageDto = z.infer<typeof attendanceExceptionPageDto>;
export type AttendanceExceptionSearchDto = Readonly<{
  from: string;
  until: string;
  kind: string | null;
  after: string | null;
}>;
