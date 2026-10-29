import type { LoadClientPolicyReview } from "../../domain/usecases/load-client-policy-review";
import type { LoadIntegrationConfiguration } from "../../domain/usecases/load-integration-configuration";
import type { LoadWorkerIntegrations } from "../../domain/usecases/load-worker-integrations";
import type { SaveClientPolicy } from "../../domain/usecases/save-client-policy";
import type { SearchAuditEvents } from "../../domain/usecases/search-audit-events";
import type { SearchPlatformAuditEvents } from "../../domain/usecases/search-platform-audit-events";

export type AdministrationUseCases = Readonly<{
  loadWorkerIntegrations: Pick<LoadWorkerIntegrations, "execute">;
  loadIntegrationConfiguration: Pick<LoadIntegrationConfiguration, "execute">;
  searchAudit: Pick<SearchAuditEvents, "execute">;
  searchPlatformAudit: Pick<SearchPlatformAuditEvents, "execute">;
  loadClientPolicy: Pick<LoadClientPolicyReview, "execute">;
  saveClientPolicy: Pick<SaveClientPolicy, "execute">;
}>;
