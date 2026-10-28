import type { Result } from "../../../../core/domain/result";
import type { IntegrationConfiguration } from "../entities/integration-configuration";

export interface IntegrationConfigurationRepository {
  read(signal: AbortSignal): Promise<Result<IntegrationConfiguration>>;
}
