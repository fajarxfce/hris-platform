import { History20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { administrationMessages } from "../i18n/administration-messages";

export function platformAuditNavigation(
  permissions: readonly string[],
  locale: Locale,
): readonly AppNavigationItem[] {
  return permissions.includes("audit.read")
    ? [
        {
          to: "/platform/audit",
          label: administrationMessages(locale).platformAudit,
          icon: <History20Regular />,
        },
      ]
    : [];
}
