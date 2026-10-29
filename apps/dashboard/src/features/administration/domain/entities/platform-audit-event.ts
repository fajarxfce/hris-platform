import type { AccountId } from "../../../../core/domain/identifiers";

/** Authorized metadata only; detailed changes have a separate disclosure contract. */
export type PlatformAuditEvent = Readonly<{
  id: string;
  actorId: AccountId;
  resourceType: string;
  resourceId: string;
  action: string;
  correlationId: string;
  recordedAt: string;
}>;
