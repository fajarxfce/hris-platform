import { People20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../domain/entities/session";
import { canManageCompanyMembers } from "../../domain/policies/company-member-policy";
import { companyMemberMessages } from "../i18n/company-member-messages";

export function identityNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canManageCompanyMembers(permissions)
      ? [
          {
            to: "/administration/members",
            label: companyMemberMessages(locale).title,
            icon: <People20Regular />,
          },
        ]
      : []),
  ];
}
