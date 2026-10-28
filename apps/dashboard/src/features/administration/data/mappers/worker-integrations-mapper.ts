import type { WorkerIntegrations } from "../../domain/entities/worker-integrations";
import { workerIntegrationsDto } from "../models/worker-integrations-dto";

export function toWorkerIntegrations(raw: unknown): WorkerIntegrations {
  const dto = workerIntegrationsDto.parse(raw);
  return Object.freeze({
    observedAt: dto.observedAt,
    workers: Object.freeze(
      dto.workers.map((worker) =>
        Object.freeze({
          ...worker,
          integrations: Object.freeze(
            worker.integrations.map((entry) => Object.freeze({ ...entry })),
          ),
        }),
      ),
    ),
  });
}
