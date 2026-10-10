import { z } from "zod";

export const organizationUnitDto = z.object({
  id: z.uuid(),
  code: z.string().min(2).max(32),
  name: z.string().min(1).max(200),
  kind: z.enum(["BRANCH", "DEPARTMENT", "POSITION", "COST_CENTER"]),
  parentId: z.uuid().nullable(),
  timezone: z.string().min(1).max(128).nullable(),
  active: z.boolean(),
  version: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
});
export type OrganizationUnitDto = z.infer<typeof organizationUnitDto>;

export const organizationUnitPageDto = z.object({
  items: z.array(organizationUnitDto).max(50),
  nextCursor: z.string().max(44).nullable(),
});
export type OrganizationUnitPageDto = z.infer<typeof organizationUnitPageDto>;

export const organizationUnitDetailsDto = z.object({
  companyId: z.uuid(),
  unit: organizationUnitDto,
  parent: organizationUnitDto.nullable(),
});
export type OrganizationUnitDetailsDto = z.infer<typeof organizationUnitDetailsDto>;
