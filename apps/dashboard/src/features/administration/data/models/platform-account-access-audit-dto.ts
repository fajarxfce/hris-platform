import { z } from "zod";

export const platformAccountAccessAuditDto = z.strictObject({
  id: z.uuid(),
  accountId: z.uuid(),
  active: z.boolean(),
  permissions: z
    .array(z.string().regex(/^[a-z][a-z0-9_.:-]{0,79}$/u))
    .max(32)
    .refine((values) => values.length <= 32 && new Set(values).size === values.length),
  reason: z
    .string()
    .max(2000)
    .refine((value) => value.length <= 2000 && [...value].length <= 1000)
    .nullable(),
});
export type PlatformAccountAccessAuditDto = z.infer<typeof platformAccountAccessAuditDto>;
