import {
  ClipboardTaskListLtr20Regular,
  People20Regular,
  TaskListSquareLtr20Regular,
} from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadAssignedLifecycle } from "../../domain/policies/lifecycle-case-policy";
import {
  canManageLifecycle,
  canReadLifecycle,
} from "../../domain/policies/lifecycle-template-policy";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";

export function lifecycleNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  return [
    ...(canReadLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/cases",
            label: lifecycleCaseMessages(locale).title,
            icon: <People20Regular />,
          },
        ]
      : []),
    ...(canReadAssignedLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/tasks",
            label: lifecycleCaseMessages(locale).queue,
            icon: <TaskListSquareLtr20Regular />,
          },
        ]
      : []),
    ...(canReadLifecycle(permissions)
      ? [
          {
            to: "/people/lifecycle/templates",
            label: lifecycleMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : canManageLifecycle(permissions)
        ? [
            {
              to: "/people/lifecycle/templates/new",
              label: lifecycleMessages(locale).create,
              icon: <ClipboardTaskListLtr20Regular />,
            },
          ]
        : []),
  ];
}
