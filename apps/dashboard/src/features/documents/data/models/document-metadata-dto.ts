import { z } from "zod";

const date = z.iso
  .date()
  .min(10)
  .max(10)
  .refine((value) => value >= "1900-01-01" && value <= "2200-12-31");
export const documentBusinessMetadataDto = z
  .object({
    category: z.enum([
      "PASSPORT",
      "NATIONAL_ID",
      "WORK_PERMIT",
      "RESIDENCE_PERMIT",
      "EMPLOYMENT_CONTRACT",
      "CERTIFICATE",
      "RECEIPT",
      "OTHER",
    ]),
    identityNumber: z
      .string()
      .min(1)
      .max(120)
      .regex(/^[^\p{Cc}]+$/u)
      .refine((value) => value === value.trim())
      .nullable(),
    issuingCountry: z
      .string()
      .regex(/^[A-Z]{2}$/u)
      .nullable(),
    issuedOn: date.nullable(),
    validUntil: date.nullable(),
  })
  .refine((value) => !value.issuedOn || !value.validUntil || value.validUntil >= value.issuedOn);
export const documentMetadataRevisionDto = z
  .object({
    documentId: z.uuid(),
    version: z.number().int().min(0).max(999),
    documentVersion: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
    metadata: documentBusinessMetadataDto,
    actorId: z.uuid().nullable(),
    recordedAt: z.iso.datetime({ offset: true }).max(40),
    reason: z.string().min(1).max(1000).nullable(),
  })
  .refine((value) =>
    value.version === 0 ? value.reason === null : value.actorId !== null && value.reason !== null,
  );
export const documentMetadataPageDto = z.object({
  items: z.array(documentMetadataRevisionDto).max(20),
  nextCursor: z
    .string()
    .regex(/^(0|[1-9]\d{0,2})$/u)
    .nullable(),
});
export type DocumentBusinessMetadataDto = z.infer<typeof documentBusinessMetadataDto>;
export type DocumentMetadataPageDto = z.infer<typeof documentMetadataPageDto>;
export type DocumentMetadataChangeDto = Readonly<{
  expectedVersion: number;
  metadata: DocumentBusinessMetadataDto;
  reason: string;
}>;
