import { z } from "zod";
import { approvalKinds, approvalStatuses } from "../../domain/entities/approval-request";

export const approvalRequestDto = z.object({
  id: z.uuid(),
  kind: z.enum(approvalKinds),
  resourceId: z.uuid(),
  authorId: z.uuid(),
  requesterId: z.uuid().nullable(),
  templateId: z.uuid(),
  templateRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  stages: z
    .array(z.object({ assignees: z.array(z.uuid()).max(200) }))
    .min(1)
    .max(8),
  currentStep: z.number().int().min(0).max(7),
  status: z.enum(approvalStatuses),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  submittedAt: z.string().max(40),
  excludedAccountIds: z.array(z.uuid()).max(200),
});
export const approvalInboxDto = z.object({
  items: z.array(approvalRequestDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export type ApprovalRequestDto = z.infer<typeof approvalRequestDto>;
export type ApprovalInboxDto = z.infer<typeof approvalInboxDto>;
