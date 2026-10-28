import { z } from "zod";
import { workerIntegrationKinds } from "../../domain/entities/worker-integrations";

const workerObservation = z
  .strictObject({
    instanceId: z.uuid(),
    firstSeenAt: z.iso.datetime(),
    lastSeenAt: z.iso.datetime(),
    status: z.enum(["RECENT", "STALE"]),
    integrations: z
      .array(z.strictObject({ kind: z.enum(workerIntegrationKinds), enabled: z.boolean() }))
      .length(4)
      .refine((entries) => new Set(entries.map((entry) => entry.kind)).size === 4),
  })
  .refine((worker) => Date.parse(worker.firstSeenAt) <= Date.parse(worker.lastSeenAt));

export const workerIntegrationsDto = z.strictObject({
  observedAt: z.iso.datetime(),
  workers: z
    .array(workerObservation)
    .max(64)
    .refine(
      (workers) => new Set(workers.map((worker) => worker.instanceId)).size === workers.length,
    ),
});
