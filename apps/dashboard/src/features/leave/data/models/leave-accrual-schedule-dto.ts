import { z } from "zod";
import { leaveBatchDto } from "./leave-batch-dto";
import { leaveTypeDto } from "./leave-type-dto";
import { leaveDateDto, leaveInstantDto, leaveVersionDto } from "./leave-value-dto";

const count = z.number().int().min(1).max(12);
const frequency = z.enum(["MONTHLY", "ANNUAL"]);
export const leaveAccrualScheduleDto = z.object({
  id: z.uuid(),
  typeId: z.uuid(),
  typeCode: leaveTypeDto.shape.code,
  typeName: z.string().min(1).max(200),
  policyVersion: leaveVersionDto,
  frequency,
  firstPeriod: z.string().length(7),
  periodCount: count,
  totalEmployees: z.number().int().min(1).max(5000),
  timezone: leaveBatchDto.shape.timezone,
  actorId: z.uuid(),
  createdAt: leaveInstantDto,
  reason: z.string().min(1).max(1000),
});
export const leaveAccrualSchedulesDto = z.object({
  items: z.array(leaveAccrualScheduleDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const leaveAccrualScheduleDetailsDto = z.object({
  schedule: leaveAccrualScheduleDto,
  periods: z
    .array(
      z.object({
        scheduledFor: leaveInstantDto,
        batch: leaveBatchDto,
        jobStatus: z.enum(["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]),
        completedEmployees: z.number().int().min(0).max(5000),
      }),
    )
    .min(1)
    .max(12),
});
export const leaveAccrualScheduleReviewDto = z.object({
  companyDate: leaveDateDto,
  timezone: leaveBatchDto.shape.timezone,
  frequency,
  policyVersion: leaveVersionDto,
  periods: z
    .array(z.object({ period: z.string().length(7), dueAt: leaveInstantDto, type: leaveTypeDto }))
    .min(1)
    .max(12),
  employeeIds: z.array(z.uuid()).min(1).max(5000),
});
export type LeaveAccrualScheduleDto = z.infer<typeof leaveAccrualScheduleDto>;
export type LeaveAccrualSchedulesDto = z.infer<typeof leaveAccrualSchedulesDto>;
export type LeaveAccrualScheduleDetailsDto = z.infer<typeof leaveAccrualScheduleDetailsDto>;
export type LeaveAccrualScheduleReviewDto = z.infer<typeof leaveAccrualScheduleReviewDto>;
export type LeaveAccrualScheduleQueryDto = Readonly<{
  typeId: string;
  firstPeriod: string;
  periods: number;
}>;
export type LeaveAccrualScheduleCreationDto = Readonly<
  LeaveAccrualScheduleQueryDto & {
    id: string;
    expectedPolicyVersion: number;
    employeeIds: readonly string[];
    reason: string;
  }
>;
