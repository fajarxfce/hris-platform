import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { IntegrationConfigurationRepository } from "../../domain/repositories/integration-configuration-repository";
import type { IntegrationConfigurationDataSource } from "../datasources/integration-configuration-data-source";
import { toIntegrationConfiguration } from "../mappers/integration-configuration-mapper";

export class RemoteIntegrationConfigurationRepository
  implements IntegrationConfigurationRepository
{
  constructor(private readonly source: IntegrationConfigurationDataSource) {}
  read(signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toIntegrationConfiguration(await this.source.read(signal)),
    );
  }
}
