import type { LoadClientPolicyReview } from "../../domain/usecases/load-client-policy-review";
import type { SaveClientPolicy } from "../../domain/usecases/save-client-policy";
import type { SearchAuditEvents } from "../../domain/usecases/search-audit-events";

export type AdministrationUseCases = Readonly<{
  searchAudit: Pick<SearchAuditEvents, "execute">;
  loadClientPolicy: Pick<LoadClientPolicyReview, "execute">;
  saveClientPolicy: Pick<SaveClientPolicy, "execute">;
}>;
