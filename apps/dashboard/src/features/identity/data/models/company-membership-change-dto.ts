import { z } from "zod";

export type CompanyMembershipChangeDto = {
  expectedVersion: number;
  active: boolean;
  permissions: string[];
  roleTemplates: { id: string; version: number; retainApplied: boolean }[];
  reason: string;
};
export const companyMembershipReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type CompanyMembershipReceiptDto = z.infer<typeof companyMembershipReceiptDto>;
