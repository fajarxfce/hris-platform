import { TaskListSquareLtr20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { jobMessages } from "../i18n/job-messages";

export function jobsNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  return [
    ...(access
      ? [
          {
            to: "/administration/jobs",
            label: jobMessages(locale).title,
            icon: <TaskListSquareLtr20Regular />,
          },
        ]
      : []),
  ];
}
