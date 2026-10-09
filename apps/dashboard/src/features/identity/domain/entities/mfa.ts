import type { OperationId } from "../../../../core/domain/identifiers";

export type MfaEnrollment = Readonly<{
  operationId: OperationId;
  secret: string;
  otpauthUri: string;
  expiresAt: string;
}>;

export type MfaVerification = Readonly<{ verifiedAt: string; recoveryCodes: readonly string[] }>;
