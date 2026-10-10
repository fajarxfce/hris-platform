import { z } from "zod";

const profileFields = {
  legalName: z.string().min(1).max(200),
  birthDate: z.string().max(10).nullable(),
  nationality: z.string().regex(/^[A-Z]{2}$/u),
  email: z.string().max(254).nullable(),
};
const version = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
export const personProfileDto = z.object({
  ...profileFields,
  personId: z.uuid(),
  ownerCompanyId: z.uuid(),
  accountId: z.uuid().nullable(),
  version,
});
export type PersonProfileDto = z.infer<typeof personProfileDto>;
export const personProfileRevisionDto = z.object({
  ...profileFields,
  revision: version,
  accountId: z.uuid().nullable(),
  actorId: z.uuid().nullable(),
  reason: z.string().min(1).max(1000),
  recordedAt: z.string().max(40),
});
export type PersonProfileRevisionDto = z.infer<typeof personProfileRevisionDto>;
export const personProfileHistoryDto = z.object({
  items: z.array(personProfileRevisionDto).max(50),
  nextCursor: z.string().max(16).nullable(),
});
export type PersonProfileHistoryDto = z.infer<typeof personProfileHistoryDto>;
export type PersonProfileChangeDto = Omit<
  PersonProfileDto,
  "personId" | "ownerCompanyId" | "accountId" | "version"
> & {
  expectedVersion: number;
  reason: string;
};
export const personProfileReceiptDto = z.object({ id: z.uuid(), version });
export type PersonProfileReceiptDto = z.infer<typeof personProfileReceiptDto>;
export type PersonAccountBindingDto = Readonly<{
  accountId: string;
  expectedVersion: number;
  reason: string;
}>;
