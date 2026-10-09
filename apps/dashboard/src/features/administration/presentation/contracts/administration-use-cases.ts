import type { LoadClientPolicyReview } from "../../domain/usecases/load-client-policy-review";
import type { SearchAuditEvents } from "../../domain/usecases/search-audit-events";

export type AdministrationUseCases = Readonly<{
  searchAudit: Pick<SearchAuditEvents, "execute">;
  loadClientPolicy: Pick<LoadClientPolicyReview, "execute">;
}>;
