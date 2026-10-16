import { Desktop20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { deviceSessionMessages } from "../i18n/device-session-messages";

export function deviceSessionNavigation(locale: Locale): readonly AppNavigationItem[] {
  return [
    {
      to: "/account/sessions",
      label: deviceSessionMessages(locale).title,
      icon: <Desktop20Regular />,
    },
  ];
}
