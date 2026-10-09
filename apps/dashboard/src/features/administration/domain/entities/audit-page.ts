import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AuditEvent } from "./audit-event";

export type AuditPage = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  items: readonly AuditEvent[];
  nextCursor: string | null;
}>;
