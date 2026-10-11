import { History20Regular, Settings20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadAudit } from "../../domain/policies/audit-search-policy";
import { canReadClientSettings } from "../../domain/policies/client-settings-policy";
import { administrationMessages } from "../i18n/administration-messages";
import { clientPolicyMessages } from "../i18n/client-policy-messages";

export function administrationNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
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
