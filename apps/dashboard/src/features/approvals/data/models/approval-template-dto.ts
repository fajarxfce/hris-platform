import { z } from "zod";
import { approvalKinds } from "../../domain/entities/approval-request";
import { approvalAssignments } from "../../domain/entities/approval-template";

export const approvalStageRuleDto = z.object({
  assignment: z.enum(approvalAssignments),
  accountIds: z.array(z.uuid()).max(25),
  permission: z.string().min(1).max(100).nullable(),
});
export const approvalTemplateDto = z.object({
  id: z.uuid(),
  name: z.string().min(1).max(200),
  kind: z.enum(approvalKinds),
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  appliedRevision: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
  effectiveFrom: z.string().regex(/^\d{4}-\d{2}-\d{2}$/u),
  category: z.string().min(1).max(80).nullable(),
  minimumAmount: z.string().regex(/^(0|[1-9]\d{0,17})(?:\.\d{1,2})?$/u),
  stages: z.array(approvalStageRuleDto).min(1).max(8),
});
export const approvalTemplatePageDto = z.object({
  items: z.array(approvalTemplateDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const approvalTemplateChangeDto = approvalTemplateDto
  .omit({ id: true, version: true, appliedRevision: true })
  .extend({
    expectedVersion: z.number().int().min(0).nullable(),
    reason: z.string(),
  });
export const approvalAdministrationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type ApprovalTemplateDto = z.infer<typeof approvalTemplateDto>;
export type ApprovalTemplatePageDto = z.infer<typeof approvalTemplatePageDto>;
export type ApprovalTemplateChangeDto = z.infer<typeof approvalTemplateChangeDto>;
export type ApprovalAdministrationReceiptDto = z.infer<typeof approvalAdministrationReceiptDto>;
export type ApprovalTemplateSearchDto = Readonly<{
  kind: string;
  asOf: string;
  after: string | null;
}>;
