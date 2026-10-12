import { z } from "zod";
import { documentRetentionDto } from "./document-retention-state-dto";

export const documentRetirementReviewDto = z.object({
  revisionId: z.uuid(),
  revisionNumber: z.number().int().min(1).max(100),
  revisionVersion: z
    .number()
    .int()
    .min(0)
    .max(Number.MAX_SAFE_INTEGER - 1),
  status: z.enum([
    "UPLOADING",
    "VALIDATING",
    "VALIDATION_FAILED",
    "READY",
    "REJECTED",
    "CANCELLED",
    "EXPIRED",
    "RETIRED",
  ]),
  published: z.boolean(),
  retention: documentRetentionDto,
  reviewedAt: z.iso.datetime({ offset: true }).max(40),
  blockingReason: z
    .string()
    .regex(/^[a-z][a-z0-9_]{0,63}$/u)
    .nullable(),
});
export const documentRetirementReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(1),
});
export type DocumentRetirementReviewDto = z.infer<typeof documentRetirementReviewDto>;
export type DocumentRetirementReceiptDto = z.infer<typeof documentRetirementReceiptDto>;
export type DocumentRetirementCommandDto = Readonly<{
  revisionId: string;
  expectedRevisionVersion: number;
  expectedRetentionVersion: number;
  reason: string;
}>;
