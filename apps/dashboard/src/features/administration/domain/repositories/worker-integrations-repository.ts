import type { Result } from "../../../../core/domain/result";
import type { WorkerIntegrations } from "../entities/worker-integrations";

export interface WorkerIntegrationsRepository {
  read(signal: AbortSignal): Promise<Result<WorkerIntegrations>>;
}
