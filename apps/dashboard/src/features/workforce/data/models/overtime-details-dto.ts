import { z } from "zod";
import { approvalStatuses } from "../../../approvals/domain/entities/approval-request";
import { overtimeChangeKinds } from "../../domain/entities/overtime-change";
import { overtimeStatuses } from "../../domain/entities/overtime-request";
import { overtimeActions } from "../../domain/entities/overtime-request-details";
import {
  overtimeIntervalDto,
  overtimeRequestDto,
  overtimeRevisionDto,
} from "./overtime-request-dto";

export const overtimeChangeDto = z.object({
  revision: overtimeRevisionDto,
  kind: z.enum(overtimeChangeKinds),
  status: z.enum(overtimeStatuses),
  actual: overtimeIntervalDto.nullable(),
  approvedMinutes: z.number().int().min(0).max(720),
  approvalId: z.uuid().nullable(),
  actorId: z.uuid(),
  recordedAt: z.string().max(40),
  reason: z.string().max(1000),
});
export const overtimeHistoryDto = z.object({
  items: z.array(overtimeChangeDto).max(50),
  nextCursor: z.string().max(16).nullable(),
});
export const overtimeWorkflowDto = z.object({
  id: z.uuid(),
  status: z.enum(approvalStatuses),
  currentStep: z.number().int().min(0).max(7),
  stages: z.array(z.array(z.uuid()).max(200)).min(1).max(8),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const overtimeDetailsDto = z.object({
  request: overtimeRequestDto,
  approval: overtimeWorkflowDto.nullable(),
  history: overtimeHistoryDto,
  actions: z.array(z.enum(overtimeActions)).max(4),
});
export type OvertimeHistoryDto = z.infer<typeof overtimeHistoryDto>;
export type OvertimeDetailsDto = z.infer<typeof overtimeDetailsDto>;
export type OvertimeWorkflowDto = z.infer<typeof overtimeWorkflowDto>;
