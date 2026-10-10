import { z } from "zod";
export type OrganizationChangeDto = Readonly<{
  code: string;
  name: string;
  kind: "BRANCH" | "DEPARTMENT" | "POSITION" | "COST_CENTER";
  parentId: string | null;
  timezone: string | null;
  active: boolean;
  expectedVersion: number | null;
}>;
export const organizationReceiptDto = z.object({
  id: z.uuid(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type OrganizationReceiptDto = z.infer<typeof organizationReceiptDto>;
