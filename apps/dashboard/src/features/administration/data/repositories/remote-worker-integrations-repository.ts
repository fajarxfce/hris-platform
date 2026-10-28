import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { WorkerIntegrationsRepository } from "../../domain/repositories/worker-integrations-repository";
import type { WorkerIntegrationsDataSource } from "../datasources/worker-integrations-data-source";
import { toWorkerIntegrations } from "../mappers/worker-integrations-mapper";

export class RemoteWorkerIntegrationsRepository implements WorkerIntegrationsRepository {
  constructor(private readonly source: WorkerIntegrationsDataSource) {}
  read(signal: AbortSignal) {
    return safeHttpCall(signal, async () => toWorkerIntegrations(await this.source.read(signal)));
  }
}
