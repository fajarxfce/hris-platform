import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { RoleTemplate } from "../../domain/entities/role-template";
import { companyMembershipMessages } from "../i18n/company-membership-messages";
import type { SelectedCompanyRole } from "./company-membership-form-values";
import { rolePermissionGroups } from "./role-template-view";

export function selectedCompanyRoleViews(roles: readonly SelectedCompanyRole[], locale: Locale) {
  const text = companyMembershipMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return roles.map((role) => ({
    ...role,
    versionLabel: `${role.retainApplied ? text.kept : text.applied}: ${number.format(role.version)}`,
    permissionsLabel: `${text.permissions}: ${number.format(role.permissions.length)}`,
    groups: rolePermissionGroups(role.permissions, role.permissions, locale),
  }));
}

export function companyRolePickerRows(
  roles: readonly RoleTemplate[],
  excluded: readonly string[],
  locale: Locale,
) {
  const text = companyMembershipMessages(locale);
  const number = new Intl.NumberFormat(locale);
  return roles.map((role) => ({
    id: role.id,
    actionLabel: `${role.name} (${role.code})`,
    actionDisabled: !role.active || excluded.includes(role.id),
    cells: [
      `${role.name} (${role.code})`,
      role.active ? text.available : text.archived,
      number.format(role.version),
      number.format(role.permissions.length),
    ],
  }));
}
