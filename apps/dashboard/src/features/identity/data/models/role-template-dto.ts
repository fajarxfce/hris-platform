import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const rolePermissionsDto = z.array(z.string().regex(/^[a-z][a-z0-9_.:-]{0,99}$/u)).max(200);
const fields = {
  code: z.string().regex(/^[A-Z0-9][A-Z0-9_-]{1,31}$/u),
  name: z.string().min(1).max(200),
  permissions: rolePermissionsDto,
  active: z.boolean(),
};
export const roleTemplateDto = z.object({ ...fields, id: z.uuid(), version });
export const roleTemplatePageDto = z.object({
  items: z.array(roleTemplateDto).max(20),
  nextCursor: z.uuid().nullable(),
});
export const roleTemplateRevisionDto = z.object({
  ...fields,
  revision: version,
  actorId: z.uuid(),
  reason: z.string().min(1).max(1000),
  recordedAt: z.string().max(40),
});
export const roleTemplateReviewDto = z.object({
  current: roleTemplateDto,
  history: z.object({
    items: z.array(roleTemplateRevisionDto).max(20),
    nextCursor: z
      .string()
      .regex(/^(?:0|[1-9]\d{0,15})$/u)
      .nullable(),
  }),
});
export const roleTemplateReceiptDto = z.object({ id: z.uuid(), version });
export type RoleTemplateDto = z.infer<typeof roleTemplateDto>;
export type RoleTemplatePageDto = z.infer<typeof roleTemplatePageDto>;
export type RoleTemplateReviewDto = z.infer<typeof roleTemplateReviewDto>;
export type RoleTemplateReceiptDto = z.infer<typeof roleTemplateReceiptDto>;
