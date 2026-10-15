import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const payrollReviewApprovalDto = z.object({
  id: z.uuid(),
  status: z.enum(["PENDING", "BLOCKED", "APPROVED", "REJECTED", "CANCELLED"]),
  version,
  currentStep: z.number().int().min(0).max(7),
  templateId: z.uuid(),
  templateRevision: version,
  stages: z.array(z.array(z.uuid()).max(200)).min(1).max(8),
  authorId: z.uuid(),
  excludedAccountIds: z.array(z.uuid()).max(201),
});
export const payrollReviewAccountDto = z.object({
  id: z.uuid(),
  displayName: z.string().min(1).max(200),
});
export type PayrollReviewApprovalDto = z.infer<typeof payrollReviewApprovalDto>;
