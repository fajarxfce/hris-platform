import { failed } from "../../../../core/domain/result";
import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import { canReadIntegrationConfiguration } from "../policies/integration-configuration-policy";
import type { WorkerIntegrationsRepository } from "../repositories/worker-integrations-repository";

export class LoadWorkerIntegrations {
  constructor(private readonly workers: WorkerIntegrationsRepository) {}
  execute(access: PlatformAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadIntegrationConfiguration(access))
      return Promise.resolve(failed("platform_administrator_required"));
    return this.workers.read(signal);
  }
}
