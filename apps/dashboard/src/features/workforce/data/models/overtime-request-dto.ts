import { z } from "zod";
import { overtimeStatuses } from "../../domain/entities/overtime-request";
import { scheduledDayDto } from "./calendar-dto";

export const overtimeRevisionDto = z.number().int().min(0).max(999);
export const overtimeIntervalDto = z.object({
  startsAt: z.string().max(40),
  endsAt: z.string().max(40),
  breakMinutes: z.number().int().min(0).max(120),
  workedMinutes: z.number().int().min(1).max(720),
});
export const overtimeRequestDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  employeeNumber: z.string().min(2).max(32),
  employeeName: z.string().min(1).max(200),
  requesterAccountId: z.uuid().nullable(),
  authorId: z.uuid(),
  createdAt: z.string().max(40),
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  timezone: z.string().min(1).max(100),
  schedule: scheduledDayDto,
  requested: overtimeIntervalDto,
  reason: z.string().min(1).max(1000),
  status: z.enum(overtimeStatuses),
  actual: overtimeIntervalDto.nullable(),
  submittedBy: z.uuid().nullable(),
  submittedAt: z.string().max(40).nullable(),
  approvalId: z.uuid().nullable(),
  approvedMinutes: z.number().int().min(0).max(720),
  decidedAt: z.string().max(40).nullable(),
  version: overtimeRevisionDto,
});
export const overtimePageDto = z.object({
  items: z.array(overtimeRequestDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export type OvertimeIntervalDto = z.infer<typeof overtimeIntervalDto>;
export type OvertimeRequestDto = z.infer<typeof overtimeRequestDto>;
export type OvertimePageDto = z.infer<typeof overtimePageDto>;
export type OvertimeSearchDto = Readonly<{
  employeeId: string | null;
  from: string;
  until: string;
  status: string | null;
  after: string | null;
}>;
