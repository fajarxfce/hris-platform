import type { HttpClient } from "../../../../core/data/http/http-client";
import type { IntegrationConfigurationDataSource } from "./integration-configuration-data-source";

export class HttpIntegrationConfigurationDataSource implements IntegrationConfigurationDataSource {
  constructor(private readonly http: HttpClient) {}
  read(signal: AbortSignal) {
    return this.http.request({ path: "/api/v1/administration/integrations/configuration" }, signal);
  }
}
