import { z } from "zod";

const permissions = z.array(z.string().max(100)).max(200);

export const sessionDto = z.object({
  account: z.object({
    id: z.uuid(),
    email: z.string().max(254),
    displayName: z.string().max(200),
    mfaConfigured: z.boolean(),
  }),
  companies: z
    .array(
      z.object({
        id: z.uuid(),
        code: z.string().max(40),
        name: z.string().max(200),
        timezone: z.string().max(100),
      }),
    )
    .max(5000),
  permissions,
  assurance: z.object({
    required: z.boolean(),
    verified: z.boolean(),
    setupAvailable: z.boolean(),
    validUntil: z.iso.datetime().nullable(),
    recentUntil: z.iso.datetime().nullable(),
  }),
});
export type SessionDto = z.infer<typeof sessionDto>;

export const companyAccessDto = z.object({ companyId: z.uuid(), permissions });
export type CompanyAccessDto = z.infer<typeof companyAccessDto>;

export const identityProvidersDto = z
  .array(
    z.object({
      id: z.string().max(100),
      name: z.string().max(200),
      authorizationPath: z.string().regex(/^\/oauth2\/authorization\/[a-zA-Z0-9_-]{1,100}$/u),
    }),
  )
  .max(20);
export type IdentityProvidersDto = z.infer<typeof identityProvidersDto>;

export const mfaEnrollmentDto = z.object({
  operationId: z.uuid(),
  secret: z.string().regex(/^[A-Z2-7]{16,128}$/u),
  otpauthUri: z.string().max(2048).startsWith("otpauth://totp/"),
  expiresAt: z.iso.datetime(),
});
export type MfaEnrollmentDto = z.infer<typeof mfaEnrollmentDto>;
export const mfaVerificationDto = z.object({
  verifiedAt: z.iso.datetime(),
  recoveryCodes: z.array(z.string().max(100)).max(20),
});
export type MfaVerificationDto = z.infer<typeof mfaVerificationDto>;
