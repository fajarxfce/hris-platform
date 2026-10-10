import { z } from "zod";
import { approvalStatuses } from "../../../approvals/domain/entities/approval-request";
import { leaveVersionDto } from "./leave-value-dto";

export const leaveWorkflowDto = z.object({
  id: z.uuid(),
  authorId: z.uuid(),
  requesterId: z.uuid().nullable(),
  status: z.enum(approvalStatuses),
  currentStep: z.number().int().min(0).max(7),
  stages: z.array(z.array(z.uuid()).max(200)).min(1).max(8),
  version: leaveVersionDto,
});
export type LeaveWorkflowDto = z.infer<typeof leaveWorkflowDto>;
