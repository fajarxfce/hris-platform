import type { IntegrationConfiguration } from "../../domain/entities/integration-configuration";
import { integrationConfigurationDto } from "../models/integration-configuration-dto";

export function toIntegrationConfiguration(raw: unknown): IntegrationConfiguration {
  const dto = integrationConfigurationDto.parse(raw);
  return Object.freeze({
    runtime: dto.runtime,
    observedAt: dto.observedAt,
    integrations: Object.freeze(dto.integrations.map((item) => Object.freeze({ ...item }))),
  });
}
