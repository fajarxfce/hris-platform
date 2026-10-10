import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const permissions = z.array(z.string().regex(/^[a-z][a-z0-9_.:-]{0,99}$/u)).max(1000);
export const companyMemberDto = z.object({
  id: z.uuid(),
  email: z.string().min(1).max(254),
  displayName: z.string().min(1).max(200),
  accountActive: z.boolean(),
  active: z.boolean(),
  permissions,
  version,
});
export const companyMemberPageDto = z.object({
  items: z.array(companyMemberDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export const companyMemberGrantDto = z.object({
  member: companyMemberDto,
  directPermissions: permissions,
  roleTemplates: z
    .array(
      z.object({
        id: z.uuid(),
        code: z.string().min(1).max(64),
        name: z.string().min(1).max(200),
        permissions,
        version,
      }),
    )
    .max(100),
});
export type CompanyMemberDto = z.infer<typeof companyMemberDto>;
export type CompanyMemberPageDto = z.infer<typeof companyMemberPageDto>;
export type CompanyMemberGrantDto = z.infer<typeof companyMemberGrantDto>;
