import { PlugConnected20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { integrationConfigurationMessages } from "../i18n/integration-configuration-messages";

export function integrationConfigurationNavigation(
  permissions: readonly string[],
  locale: Locale,
): readonly AppNavigationItem[] {
  return permissions.includes("identity.manage")
    ? [
        {
          to: "/platform/integrations",
          label: integrationConfigurationMessages(locale).title,
          icon: <PlugConnected20Regular />,
        },
      ]
    : [];
}
