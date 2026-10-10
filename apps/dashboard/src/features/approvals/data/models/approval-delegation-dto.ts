import { z } from "zod";
import { approvalKinds } from "../../domain/entities/approval-request";

const instant = z
  .string()
  .max(32)
  .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/u);
export const approvalDelegationDto = z.object({
  id: z.uuid(),
  kind: z.enum(approvalKinds),
  fromAccount: z.uuid(),
  toAccount: z.uuid(),
  validFrom: instant,
  validUntil: instant,
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export const approvalDelegationPageDto = z.object({
  items: z.array(approvalDelegationDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export type ApprovalDelegationDto = z.infer<typeof approvalDelegationDto>;
export type ApprovalDelegationPageDto = z.infer<typeof approvalDelegationPageDto>;
export type ApprovalDelegationChangeDto = Omit<ApprovalDelegationDto, "id" | "version"> &
  Readonly<{
    expectedVersion: number | null;
    reason: string;
  }>;
