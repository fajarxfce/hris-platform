import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAuditDataSource } from "../data/datasources/http-audit-data-source";
import { HttpClientPolicyDataSource } from "../data/datasources/http-client-policy-data-source";
import { HttpIntegrationConfigurationDataSource } from "../data/datasources/http-integration-configuration-data-source";
import { HttpPlatformAuditDataSource } from "../data/datasources/http-platform-audit-data-source";
import { HttpWorkerIntegrationsDataSource } from "../data/datasources/http-worker-integrations-data-source";
import { RemoteAuditRepository } from "../data/repositories/remote-audit-repository";
import { RemoteClientPolicyRepository } from "../data/repositories/remote-client-policy-repository";
import { RemoteIntegrationConfigurationRepository } from "../data/repositories/remote-integration-configuration-repository";
import { RemotePlatformAuditRepository } from "../data/repositories/remote-platform-audit-repository";
import { RemoteWorkerIntegrationsRepository } from "../data/repositories/remote-worker-integrations-repository";
import { GetPlatformAccountAccessAudit } from "../domain/usecases/get-platform-account-access-audit";
import { LoadClientPolicyReview } from "../domain/usecases/load-client-policy-review";
import { LoadIntegrationConfiguration } from "../domain/usecases/load-integration-configuration";
import { LoadWorkerIntegrations } from "../domain/usecases/load-worker-integrations";
import { SaveClientPolicy } from "../domain/usecases/save-client-policy";
import { SearchAuditEvents } from "../domain/usecases/search-audit-events";
import { SearchPlatformAuditEvents } from "../domain/usecases/search-platform-audit-events";

export function createAdministrationFeature(http: HttpClient) {
  const source = new HttpAuditDataSource(http);
  const audits = new RemoteAuditRepository(source);
  const policies = new RemoteClientPolicyRepository(new HttpClientPolicyDataSource(http));
  const platformAudits = new RemotePlatformAuditRepository(new HttpPlatformAuditDataSource(http));
  return {
    loadWorkerIntegrations: new LoadWorkerIntegrations(
      new RemoteWorkerIntegrationsRepository(new HttpWorkerIntegrationsDataSource(http)),
    ),
    loadIntegrationConfiguration: new LoadIntegrationConfiguration(
      new RemoteIntegrationConfigurationRepository(
        new HttpIntegrationConfigurationDataSource(http),
      ),
    ),
    searchAudit: new SearchAuditEvents(audits),
    searchPlatformAudit: new SearchPlatformAuditEvents(platformAudits),
    getPlatformAccountAudit: new GetPlatformAccountAccessAudit(platformAudits),
    loadClientPolicy: new LoadClientPolicyReview(policies),
    saveClientPolicy: new SaveClientPolicy(policies),
  };
}
