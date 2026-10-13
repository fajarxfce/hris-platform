import { z } from "zod";
import { maximumBinaryFileBytes } from "../../../../core/domain/files/binary-file";
import { approvalStatuses } from "../../../approvals/domain/entities/approval-request";
import { expenseClaimStatuses } from "../../domain/entities/expense-claim";
import {
  expenseAmountDto,
  expenseInstantDto,
  expenseSubmissionSummaryDto,
  expenseVersionDto,
} from "./expense-claim-dto";
import { expenseCategoryDto } from "./expense-policy-dto";
export const expenseReceiptMetadataDto = z.object({
  revisionId: z.uuid(),
  fileName: z
    .string()
    .min(1)
    .max(180)
    .regex(/^[^\p{Cc}/\\]+$/u),
  mediaType: z.enum(["application/pdf", "image/jpeg", "image/png"]),
  size: z.number().int().min(1).max(maximumBinaryFileBytes),
  sha256: z.string().regex(/^[0-9a-f]{64}$/u),
  possibleDuplicate: z.boolean(),
});
const digestSet = z.array(z.string().regex(/^[0-9a-f]{64}$/u)).max(60);
export const expenseSubmissionDto = expenseSubmissionSummaryDto.omit({ approvalId: true }).extend({
  claimId: z.uuid(),
  employmentId: z.uuid(),
  requesterId: z.uuid().nullable(),
  employeeNumber: z.string().min(1).max(32),
  employeeName: z.string().min(1).max(200),
  description: z.string().max(2000),
  makerIds: z.array(z.uuid()).min(1).max(100),
  reason: z.string().min(1).max(1000),
  lines: z
    .array(
      z.object({
        id: z.uuid(),
        occurredOn: z.iso.date(),
        amount: expenseAmountDto,
        description: z.string().min(1).max(500),
        category: expenseCategoryDto,
        costCenter: z
          .object({
            id: z.uuid(),
            code: z.string().min(1).max(32),
            name: z.string().min(1).max(200),
            version: expenseVersionDto,
          })
          .nullable(),
        receipts: z.array(expenseReceiptMetadataDto).max(3),
      }),
    )
    .min(1)
    .max(20),
  approval: z.object({
    id: z.uuid(),
    status: z.enum(approvalStatuses),
    version: expenseVersionDto,
    currentStep: z.number().int().min(0).max(8),
    templateId: z.uuid(),
    templateRevision: expenseVersionDto,
    stages: z.array(z.array(z.uuid()).max(25)).min(1).max(8),
  }),
  claimVersion: expenseVersionDto,
  claimStatus: z.enum(expenseClaimStatuses),
  current: z.boolean(),
  canReadClaim: z.boolean(),
  availableActions: z.array(z.literal("DECIDE")).max(1),
  reviews: z
    .array(
      z.object({
        claimVersion: expenseVersionDto,
        approvalVersion: expenseVersionDto,
        step: z.number().int().min(0).max(7),
        actorId: z.uuid(),
        decidingFor: z.uuid(),
        decision: z.enum(["APPROVE", "REJECT", "RETURN"]),
        status: z.enum(expenseClaimStatuses),
        duplicateDigests: digestSet,
        duplicatesAcknowledged: z.boolean(),
        reason: z.string().max(1000),
        decidedAt: expenseInstantDto,
      }),
    )
    .max(8),
  duplicateReceiptDigests: digestSet,
});
export type ExpenseReceiptMetadataDto = z.infer<typeof expenseReceiptMetadataDto>;
export type ExpenseSubmissionDto = z.infer<typeof expenseSubmissionDto>;
