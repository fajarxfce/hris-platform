import { z } from "zod";
import { leaveStatuses } from "../../domain/entities/leave-request";
import { leaveActions } from "../../domain/entities/leave-request-details";
import { leaveAttachmentDto } from "./leave-attachment-dto";
import { leaveDayDto } from "./leave-day-dto";
import { leaveHistoryDto } from "./leave-history-dto";
import { leavePolicyDto } from "./leave-policy-dto";
import { leaveDayQuantityDto, leaveInstantDto, leaveVersionDto } from "./leave-value-dto";
import { leaveWorkflowDto } from "./leave-workflow-dto";

export const leaveRequestDetailsDto = z.object({
  id: z.uuid(),
  employeeId: z.uuid(),
  employeeNumber: z.string().min(1).max(40),
  employeeName: z.string().min(1).max(200),
  ownerAccountId: z.uuid().nullable(),
  authorId: z.uuid(),
  submittedAt: leaveInstantDto,
  policy: leavePolicyDto,
  days: z.array(leaveDayDto).min(1).max(366),
  chargedDays: leaveDayQuantityDto,
  reason: z.string().min(1).max(1000),
  status: z.enum(leaveStatuses),
  version: leaveVersionDto,
  approval: leaveWorkflowDto,
  cancellation: leaveWorkflowDto.nullable(),
  history: leaveHistoryDto,
  availableActions: z.array(z.enum(leaveActions)).max(3),
  attachments: z.array(leaveAttachmentDto).max(3),
});
export type LeaveRequestDetailsDto = z.infer<typeof leaveRequestDetailsDto>;
