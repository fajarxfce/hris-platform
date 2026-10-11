import { ClipboardTaskListLtr20Regular, People20Regular } from "@fluentui/react-icons";
import type { AppNavigationItem } from "../../../../core/presentation/components/app-portal-shell";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canCreateEmployee } from "../../domain/policies/employee-creation-policy";
import { canImportEmployees } from "../../domain/policies/employee-import-policy";
import { canReadEmployees } from "../../domain/policies/employee-policy";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";

export function peopleNavigation(
  access: CompanyAccess | null,
  locale: Locale,
): readonly AppNavigationItem[] {
  const permissions = access?.permissions ?? [];
  const text = messages(locale);
  return [
    ...(canReadEmployees(permissions)
      ? [{ to: "/people/employees", label: text.people, icon: <People20Regular /> }]
      : canCreateEmployee(permissions)
        ? [
            {
              to: "/people/employees/new",
              label: employeeCreationMessages(locale).create,
              icon: <People20Regular />,
            },
          ]
        : []),
    ...(canImportEmployees(permissions)
      ? [
          {
            to: "/people/imports",
            label: employeeImportMessages(locale).title,
            icon: <ClipboardTaskListLtr20Regular />,
          },
        ]
      : []),
  ];
}
