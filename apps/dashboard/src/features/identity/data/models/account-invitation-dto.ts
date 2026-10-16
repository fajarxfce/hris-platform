import { z } from "zod";
export type AccountInvitationDto = Readonly<{
  id: string;
  email: string;
  displayName: string;
  reason: string;
  expectedVersion: number | null;
}>;
export const invitationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type InvitationReceiptDto = z.infer<typeof invitationReceiptDto>;
