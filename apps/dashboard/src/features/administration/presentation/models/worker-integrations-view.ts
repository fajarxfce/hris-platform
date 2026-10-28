import type { Locale } from "../../../../core/presentation/i18n/messages";
import {
  type WorkerIntegrations,
  workerIntegrationKinds,
} from "../../domain/entities/worker-integrations";
import { integrationConfigurationMessages } from "../i18n/integration-configuration-messages";
import { workerIntegrationsMessages } from "../i18n/worker-integrations-messages";

export function workerIntegrationsView(review: WorkerIntegrations, locale: Locale) {
  const text = workerIntegrationsMessages(locale),
    configuration = integrationConfigurationMessages(locale);
  const format = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  return {
    observedAt: format.format(new Date(review.observedAt)),
    columns: [
      { id: "worker", label: text.worker },
      { id: "status", label: text.status },
      { id: "first", label: text.firstSeen },
      { id: "last", label: text.lastSeen },
      ...workerIntegrationKinds.map((kind) => ({ id: kind, label: configuration.kinds[kind] })),
    ],
    rows: review.workers.map((worker) => ({
      id: worker.instanceId,
      cells: [
        worker.instanceId,
        worker.status === "RECENT" ? text.recent : text.stale,
        format.format(new Date(worker.firstSeenAt)),
        format.format(new Date(worker.lastSeenAt)),
        ...workerIntegrationKinds.map((kind) =>
          worker.integrations.find((entry) => entry.kind === kind)?.enabled
            ? configuration.enabled
            : configuration.disabled,
        ),
      ],
    })),
  };
}
