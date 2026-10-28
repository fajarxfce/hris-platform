import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpAuditDataSource } from "../data/datasources/http-audit-data-source";
import { HttpClientPolicyDataSource } from "../data/datasources/http-client-policy-data-source";
import { HttpIntegrationConfigurationDataSource } from "../data/datasources/http-integration-configuration-data-source";
import { RemoteAuditRepository } from "../data/repositories/remote-audit-repository";
import { RemoteClientPolicyRepository } from "../data/repositories/remote-client-policy-repository";
import { RemoteIntegrationConfigurationRepository } from "../data/repositories/remote-integration-configuration-repository";
import { LoadClientPolicyReview } from "../domain/usecases/load-client-policy-review";
import { LoadIntegrationConfiguration } from "../domain/usecases/load-integration-configuration";
import { SaveClientPolicy } from "../domain/usecases/save-client-policy";
import { SearchAuditEvents } from "../domain/usecases/search-audit-events";

export function createAdministrationFeature(http: HttpClient) {
  const source = new HttpAuditDataSource(http);
  const audits = new RemoteAuditRepository(source);
  const policies = new RemoteClientPolicyRepository(new HttpClientPolicyDataSource(http));
  return {
    loadIntegrationConfiguration: new LoadIntegrationConfiguration(
      new RemoteIntegrationConfigurationRepository(
        new HttpIntegrationConfigurationDataSource(http),
      ),
    ),
    searchAudit: new SearchAuditEvents(audits),
    loadClientPolicy: new LoadClientPolicyReview(policies),
    saveClientPolicy: new SaveClientPolicy(policies),
  };
}
