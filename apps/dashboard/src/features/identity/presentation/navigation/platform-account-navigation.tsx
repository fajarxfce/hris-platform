import { People20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";

export function platformAccountNavigation(
  permissions: readonly string[],
  locale: Locale,
): readonly AppNavigationItem[] {
  return permissions.includes("identity.manage")
    ? [
        {
          to: "/platform/accounts",
          label: managedAccountMessages(locale).title,
          icon: <People20Regular />,
        },
      ]
    : [];
}
