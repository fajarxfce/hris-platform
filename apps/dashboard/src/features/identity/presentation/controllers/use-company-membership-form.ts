import { useMemo, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { RoleTemplate } from "../../domain/entities/role-template";
import type { CompanyMembershipEditorState } from "../models/company-membership-editor-state";
import {
  type CompanyMembershipFormValues,
  companyMembershipFormValues,
} from "../models/company-membership-form-values";
import { selectedCompanyRoleViews } from "../models/company-membership-view";
import { rolePermissionGroups } from "../models/role-template-view";
import type { CompanyMembershipEditorController } from "./company-membership-editor-controller";

type RolePicker = Readonly<{ replacing: string | null }> | null;
export function useCompanyMembershipForm(
  controller: CompanyMembershipEditorController,
  state: CompanyMembershipEditorState,
  companyId: CompanyId,
  locale: Locale,
) {
  const values = useMemo(() => companyMembershipFormValues(state.grant), [state.grant]);
  const form = useForm<CompanyMembershipFormValues>({ values });
  const active = useController({ name: "active", control: form.control });
  const permissions = useController({ name: "permissions", control: form.control });
  const roles = useController({ name: "roleTemplates", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const [picker, setPicker] = useReducer((_old: RolePicker, next: RolePicker) => next, null);
  const editable = state.stage === "editing";
  const requiresRoleReview =
    active.field.value &&
    state.grant?.member.membershipActive === false &&
    roles.field.value.some((role) => role.retainApplied);
  const blockedByAccount = active.field.value && state.grant?.member.accountActive === false;
  const groups = useMemo(
    () =>
      rolePermissionGroups(
        [...new Set([...state.permissions, ...permissions.field.value])],
        permissions.field.value,
        locale,
      ),
    [state.permissions, permissions.field.value, locale],
  );
  const effectiveGroups = useMemo(() => {
    const effective = [
      ...new Set([
        ...permissions.field.value,
        ...roles.field.value.flatMap((role) => role.permissions),
      ]),
    ];
    return rolePermissionGroups(effective, effective, locale);
  }, [permissions.field.value, roles.field.value, locale]);
  const roleViews = useMemo(
    () => selectedCompanyRoleViews(roles.field.value, locale),
    [roles.field.value, locale],
  );
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    active: active.field,
    reason: reason.field,
    groups,
    effectiveGroups,
    roles: roles.field.value,
    roleViews,
    excludedRoleIds: roles.field.value
      .filter((role) => role.id !== picker?.replacing)
      .map((role) => role.id),
    picker: editable ? picker : null,
    requiresRoleReview,
    blockedByAccount,
    editable,
    canAddRole: editable && roles.field.value.length < 8,
    canSave: editable && !requiresRoleReview && !blockedByAccount,
    submit: form.handleSubmit((fields) => {
      if (!requiresRoleReview && !blockedByAccount) return controller.save(fields);
    }),
    selectPermission: (permission: string, selected: boolean) => {
      if (!editable || (selected && !state.permissions.includes(permission))) return;
      permissions.field.onChange(
        selected
          ? [...new Set([...permissions.field.value, permission])]
          : permissions.field.value.filter((value) => value !== permission),
      );
    },
    removeRole: (id: string) => {
      if (editable) roles.field.onChange(roles.field.value.filter((role) => role.id !== id));
    },
    openPicker: (replacing: string | null) => {
      if (editable && (replacing !== null || roles.field.value.length < 8))
        setPicker({ replacing });
    },
    closePicker: () => setPicker(null),
    selectRole: (role: RoleTemplate) => {
      if (!editable || !picker || !role.active || role.companyId !== companyId) return;
      const selected = roles.field.value.filter((value) => value.id !== picker.replacing);
      if (selected.length >= 8 || selected.some((value) => value.id === role.id)) return;
      roles.field.onChange([
        ...selected,
        {
          id: role.id,
          code: role.code,
          name: role.name,
          version: role.version,
          permissions: role.permissions,
          retainApplied: false,
        },
      ]);
      setPicker(null);
    },
    refresh: () =>
      depart(() => {
        setPicker(null);
        void controller.refresh();
      }),
  };
}
