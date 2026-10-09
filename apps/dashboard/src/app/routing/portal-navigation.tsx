import { DataBarVertical20Regular, Home20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import { canReadHeadcount } from "../../features/reporting/domain/policies/headcount-policy";

/** Navigation visibility uses the same client policy as the feature; the API enforces access. */
export function portalNavigation(
  permissions: readonly string[],
  locale: Locale,
): readonly AppNavigationItem[] {
  const text = messages(locale);
  return [
    { to: "/", label: text.overview, icon: <Home20Regular /> },
    ...(canReadHeadcount(permissions)
      ? [{ to: "/reports/headcount", label: text.reports, icon: <DataBarVertical20Regular /> }]
      : []),
  ];
}
