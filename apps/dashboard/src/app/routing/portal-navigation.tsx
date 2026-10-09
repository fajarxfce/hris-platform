import {
  DataBarVertical20Regular,
  History20Regular,
  Home20Regular,
  Settings20Regular,
  TaskListSquareLtr20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../core/presentation/i18n/messages";
import { canReadAudit } from "../../features/administration/domain/policies/audit-search-policy";
import { canReadClientSettings } from "../../features/administration/domain/policies/client-settings-policy";
import { administrationMessages } from "../../features/administration/presentation/i18n/administration-messages";
import { clientPolicyMessages } from "../../features/administration/presentation/i18n/client-policy-messages";
import type { CompanyAccess } from "../../features/identity/domain/entities/session";
import { jobMessages } from "../../features/jobs/presentation/i18n/job-messages";
import { canReadHeadcount } from "../../features/reporting/domain/policies/headcount-policy";

/** Navigation visibility uses the same client policy as the feature; the API enforces access. */
export function portalNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const text = messages(locale);
  const permissions = access?.permissions ?? [];
  return [
    { to: "/", label: text.overview, icon: <Home20Regular /> },
    ...(access
      ? [
          {
            to: "/administration/jobs",
            label: jobMessages(locale).title,
            icon: <TaskListSquareLtr20Regular />,
          },
        ]
      : []),
    ...(canReadHeadcount(permissions)
      ? [{ to: "/reports/headcount", label: text.reports, icon: <DataBarVertical20Regular /> }]
      : []),
    ...(canReadAudit(permissions)
      ? [
          {
            to: "/administration/audit",
            label: administrationMessages(locale).audit,
            icon: <History20Regular />,
          },
        ]
      : []),
    ...(canReadClientSettings(permissions)
      ? [
          {
            to: "/settings/client-policy",
            label: clientPolicyMessages(locale).title,
            icon: <Settings20Regular />,
          },
        ]
      : []),
  ];
}
