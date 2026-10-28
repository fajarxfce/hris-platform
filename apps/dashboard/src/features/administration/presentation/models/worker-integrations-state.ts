import type { Failure } from "../../../../core/domain/result";
import type { WorkerIntegrations } from "../../domain/entities/worker-integrations";

export type WorkerIntegrationsState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  review: WorkerIntegrations | null;
  failure: Failure | null;
}>;
export const initialWorkerIntegrationsState: WorkerIntegrationsState = Object.freeze({
  stage: "idle",
  review: null,
  failure: null,
});
