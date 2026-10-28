export interface WorkerIntegrationsDataSource {
  read(signal: AbortSignal): Promise<unknown>;
}
