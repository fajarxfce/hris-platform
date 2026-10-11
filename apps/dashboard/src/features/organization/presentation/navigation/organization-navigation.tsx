import { Organization20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadOrganization } from "../../domain/policies/organization-unit-policy";
import { organizationMessages } from "../i18n/organization-messages";

export function organizationNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canReadOrganization(permissions)
      ? [
          {
            to: "/organization/units",
            label: organizationMessages(locale).title,
            icon: <Organization20Regular />,
          },
        ]
      : []),
  ];
}
