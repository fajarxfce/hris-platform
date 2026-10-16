import { z } from "zod";
import { workDayFacts } from "../../domain/entities/closed-work-day";
import { scheduledDayDto, workforceEmployeeDto } from "./calendar-dto";
import { overtimeIntervalDto, overtimeRevisionDto } from "./overtime-request-dto";
import { workPeriodDto } from "./work-period-dto";

export const closedWorkDayDto = z.object({
  workDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  fact: z.enum(workDayFacts),
  acceptedMinutes: z.number().int().min(0).max(1440),
  schedule: scheduledDayDto,
  evidenceIds: z.array(z.uuid()).max(32),
  correctionId: z.uuid().nullable(),
  overtime: z
    .array(
      z.object({
        requestId: z.uuid(),
        revision: overtimeRevisionDto,
        actual: overtimeIntervalDto,
        approvedMinutes: z.number().int().min(1).max(720),
        schedule: scheduledDayDto,
      }),
    )
    .max(128),
});
export type ClosedWorkDayDto = z.infer<typeof closedWorkDayDto>;

export const workPeriodSnapshotDto = z.object({
  employeeId: z.uuid(),
  month: z.string().regex(/^\d{4}-\d{2}$/u),
  employee: workforceEmployeeDto,
  period: workPeriodDto,
  adjustmentsSupported: z.boolean().default(false),
  days: z.array(closedWorkDayDto).min(28).max(31),
});
export type WorkPeriodSnapshotDto = z.infer<typeof workPeriodSnapshotDto>;
