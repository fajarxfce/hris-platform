import { Megaphone20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageAnnouncements } from "../../domain/policies/announcement-read-policy";
import { announcementMessages } from "../i18n/announcement-messages";

export function communicationsNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canManageAnnouncements(permissions)
      ? [
          {
            to: "/communications/announcements",
            label: announcementMessages(locale).title,
            icon: <Megaphone20Regular />,
          },
        ]
      : []),
  ];
}
