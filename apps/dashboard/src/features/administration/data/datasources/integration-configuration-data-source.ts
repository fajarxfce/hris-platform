export interface IntegrationConfigurationDataSource {
  read(signal: AbortSignal): Promise<unknown>;
}
