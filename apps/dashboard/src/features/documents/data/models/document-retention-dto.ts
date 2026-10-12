import { z } from "zod";

export const documentRetentionPolicyDto = z.object({
  id: z.uuid(),
  classification: z.enum(["PERSONAL", "RECEIPT", "HR_ONLY"]),
  version: z.number().int().min(0).max(9999),
  retentionDays: z.number().int().min(1).max(36500).nullable(),
  actorId: z.uuid(),
  recordedAt: z.iso.datetime({ offset: true }).max(40),
  reason: z
    .string()
    .min(1)
    .max(1000)
    .regex(/^[^\p{Cc}]+$/u),
});
export const documentRetentionPoliciesDto = z.array(documentRetentionPolicyDto).max(3);
export const documentRetentionPolicyPageDto = z.object({
  items: z.array(documentRetentionPolicyDto).max(20),
  nextCursor: z
    .string()
    .regex(/^(0|[1-9]\d{0,3})$/u)
    .nullable(),
});
export const documentRetentionReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(9999),
});
export type DocumentRetentionPolicyDto = z.infer<typeof documentRetentionPolicyDto>;
export type DocumentRetentionPolicyPageDto = z.infer<typeof documentRetentionPolicyPageDto>;
export type DocumentRetentionReceiptDto = z.infer<typeof documentRetentionReceiptDto>;
export type DocumentRetentionPolicyChangeDto = Readonly<{
  policyId: string;
  classification: "PERSONAL" | "RECEIPT" | "HR_ONLY";
  expectedVersion: number | null;
  retentionDays: number | null;
  reason: string;
}>;
