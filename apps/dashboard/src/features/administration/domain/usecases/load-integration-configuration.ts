import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import { canReadIntegrationConfiguration } from "../policies/integration-configuration-policy";
import type { IntegrationConfigurationRepository } from "../repositories/integration-configuration-repository";

export class LoadIntegrationConfiguration {
  constructor(private readonly configuration: IntegrationConfigurationRepository) {}
  execute(access: PlatformAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadIntegrationConfiguration(access))
      return Promise.resolve(failed("platform_administrator_required"));
    return this.configuration.read(signal);
  }
}
