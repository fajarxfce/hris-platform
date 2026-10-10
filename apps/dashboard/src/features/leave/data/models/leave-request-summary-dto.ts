import { z } from "zod";
import { leaveStatuses } from "../../domain/entities/leave-request";
import {
  leaveDateDto,
  leaveDayQuantityDto,
  leaveInstantDto,
  leaveVersionDto,
} from "./leave-value-dto";

export const leaveRequestSummaryDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  employeeNumber: z.string().min(1).max(40),
  employeeName: z.string().min(1).max(200),
  typeCode: z.string().min(1).max(32),
  typeName: z.string().min(1).max(200),
  from: leaveDateDto,
  until: leaveDateDto,
  chargedDays: leaveDayQuantityDto,
  status: z.enum(leaveStatuses),
  submittedAt: leaveInstantDto,
  version: leaveVersionDto,
});
export const leaveRequestPageDto = z.object({
  items: z.array(leaveRequestSummaryDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export type LeaveRequestSummaryDto = z.infer<typeof leaveRequestSummaryDto>;
export type LeaveRequestPageDto = z.infer<typeof leaveRequestPageDto>;
