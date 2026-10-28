export const workerIntegrationKinds = ["MAIL", "PUSH", "STORAGE", "DOCUMENT_SCANNER"] as const;
export type WorkerIntegrationKind = (typeof workerIntegrationKinds)[number];
export type WorkerIntegrationObservation = Readonly<{
  instanceId: string;
  firstSeenAt: string;
  lastSeenAt: string;
  status: "RECENT" | "STALE";
  integrations: readonly Readonly<{ kind: WorkerIntegrationKind; enabled: boolean }>[];
}>;
export type WorkerIntegrations = Readonly<{
  observedAt: string;
  workers: readonly WorkerIntegrationObservation[];
}>;
