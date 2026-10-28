import type { LoadClientPolicyReview } from "../../domain/usecases/load-client-policy-review";
import type { LoadIntegrationConfiguration } from "../../domain/usecases/load-integration-configuration";
import type { SaveClientPolicy } from "../../domain/usecases/save-client-policy";
import type { SearchAuditEvents } from "../../domain/usecases/search-audit-events";

export type AdministrationUseCases = Readonly<{
  loadIntegrationConfiguration: Pick<LoadIntegrationConfiguration, "execute">;
  searchAudit: Pick<SearchAuditEvents, "execute">;
  loadClientPolicy: Pick<LoadClientPolicyReview, "execute">;
  saveClientPolicy: Pick<SaveClientPolicy, "execute">;
}>;
