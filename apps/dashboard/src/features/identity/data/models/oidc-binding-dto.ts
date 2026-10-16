import { z } from "zod";

const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const oidcBindingDto = z.object({
  id: z.uuid(),
  issuer: z.string().min(1).max(500),
  subject: z.string().min(1).max(255),
  active: z.boolean(),
  version,
  linkedAt: z.iso.datetime({ offset: true }).max(40),
});
export const oidcBindingPageDto = z.object({
  items: z.array(oidcBindingDto).max(50),
  nextCursor: z.uuid().nullable(),
});
export const oidcBindingReceiptDto = z.object({ id: z.uuid(), version });
export type OidcBindingDto = z.infer<typeof oidcBindingDto>;
export type OidcBindingPageDto = z.infer<typeof oidcBindingPageDto>;
export type OidcBindingReceiptDto = z.infer<typeof oidcBindingReceiptDto>;
