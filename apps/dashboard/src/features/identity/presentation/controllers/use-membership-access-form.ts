import { useMemo, useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { RoleTemplate } from "../../domain/entities/role-template";
import type { CompanyMembershipEditorState } from "../models/company-membership-editor-state";
import type { CompanyMembershipFormValues } from "../models/company-membership-form-values";
import { selectedCompanyRoleViews } from "../models/company-membership-view";
import { rolePermissionGroups } from "../models/role-template-view";

type RolePicker = Readonly<{ replacing: string | null }> | null;
export function useMembershipAccessForm({
  stage,
  values,
  assignablePermissions,
  accountActive,
  previousMembershipActive,
  companyId,
  locale,
  onSave,
  onRefresh,
}: {
  stage: CompanyMembershipEditorState["stage"];
  values: CompanyMembershipFormValues;
  assignablePermissions: readonly string[];
  accountActive: boolean;
  previousMembershipActive: boolean | null;
  companyId: CompanyId;
  locale: Locale;
  onSave: (fields: CompanyMembershipFormValues) => Promise<void>;
  onRefresh: () => Promise<void>;
}) {
  const form = useForm<CompanyMembershipFormValues>({ values });
  const active = useController({ name: "active", control: form.control });
  const permissions = useController({ name: "permissions", control: form.control });
  const roles = useController({ name: "roleTemplates", control: form.control });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { required: true, maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
  const [picker, setPicker] = useReducer((_old: RolePicker, next: RolePicker) => next, null);
  const editable = stage === "editing";
  const requiresRoleReview =
    active.field.value &&
    previousMembershipActive === false &&
    roles.field.value.some((role) => role.retainApplied);
  const blockedByAccount = active.field.value && accountActive === false;
  const groups = useMemo(
    () =>
      rolePermissionGroups(
        [...new Set([...assignablePermissions, ...permissions.field.value])],
        permissions.field.value,
        locale,
      ),
    [assignablePermissions, permissions.field.value, locale],
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
    stage === "saving"
      ? "pending"
      : stage === "unconfirmed"
        ? "unconfirmed"
        : stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    active: active.field,
    reason: reason.field,
    reasonInvalid: reason.fieldState.invalid,
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
      if (editable && !requiresRoleReview && !blockedByAccount) return onSave(fields);
    }),
    selectPermission: (permission: string, selected: boolean) => {
      if (!editable || (selected && !assignablePermissions.includes(permission))) return;
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
        void onRefresh();
      }),
  };
}
