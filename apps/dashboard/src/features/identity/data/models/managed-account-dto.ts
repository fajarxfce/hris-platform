import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const permissions = z.array(z.string().regex(/^[a-z][a-z0-9_.:-]{0,99}$/u)).max(100);
export const managedAccountDto = z.object({
  id: z.uuid(),
  email: z.string().min(1).max(254),
  displayName: z.string().min(1).max(200),
  active: z.boolean(),
  invitationPending: z.boolean(),
  mfaConfigured: z.boolean(),
  platformPermissions: permissions,
  version,
});
export const managedAccountPageDto = z.object({
  items: z.array(managedAccountDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export const managedAccountReviewDto = z.object({
  account: managedAccountDto,
  assignablePermissions: permissions,
});
export const accountAccessReceiptDto = z.object({ id: z.uuid(), version });
export type ManagedAccountDto = z.infer<typeof managedAccountDto>;
export type ManagedAccountPageDto = z.infer<typeof managedAccountPageDto>;
export type ManagedAccountReviewDto = z.infer<typeof managedAccountReviewDto>;
export type AccountAccessReceiptDto = z.infer<typeof accountAccessReceiptDto>;
