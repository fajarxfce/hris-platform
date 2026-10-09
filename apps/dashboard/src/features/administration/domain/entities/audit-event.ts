import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";

/** Authorized metadata only; detailed changes have a separate disclosure contract. */
export type AuditEvent = Readonly<{
  id: string;
  companyId: CompanyId;
  actorId: AccountId;
  resourceType: string;
  resourceId: string;
  action: string;
  correlationId: string;
  recordedAt: string;
}>;
