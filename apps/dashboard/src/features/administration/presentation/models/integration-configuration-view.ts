import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { IntegrationConfiguration } from "../../domain/entities/integration-configuration";
import { integrationConfigurationMessages } from "../i18n/integration-configuration-messages";

export function integrationConfigurationView(review: IntegrationConfiguration, locale: Locale) {
  const text = integrationConfigurationMessages(locale);
  return {
    observedAt: new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "medium",
      timeZone: "UTC",
    }).format(new Date(review.observedAt)),
    rows: review.integrations.map((entry) => ({
      id: entry.kind,
      cells: [text.kinds[entry.kind], entry.enabled ? text.enabled : text.disabled],
    })),
  };
}
