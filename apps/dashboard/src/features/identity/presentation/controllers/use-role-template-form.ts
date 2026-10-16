import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { RoleTemplateEditorState } from "../models/role-template-editor-state";
import {
  type RoleTemplateFormValues,
  roleTemplateFormValues,
} from "../models/role-template-form-values";
import { rolePermissionGroups } from "../models/role-template-view";
import type { RoleTemplateEditorController } from "./role-template-editor-controller";
export function useRoleTemplateForm(
  controller: RoleTemplateEditorController,
  state: RoleTemplateEditorState,
  locale: Locale,
) {
  const values = useMemo(() => roleTemplateFormValues(state.template), [state.template]);
  const form = useForm<RoleTemplateFormValues>({ values });
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const permissions = useController({ name: "permissions", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const groups = useMemo(
    () =>
      rolePermissionGroups(
        [...new Set([...state.permissions, ...permissions.field.value])],
        permissions.field.value,
        locale,
      ),
    [state.permissions, permissions.field.value, locale],
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
    code: code.field,
    name: name.field,
    active: active.field,
    reason: reason.field,
    groups,
    editable: state.stage === "editing",
    submit: form.handleSubmit(controller.save),
    selectPermission: (permission: string, selected: boolean) => {
      if (state.stage !== "editing" || (selected && !state.permissions.includes(permission)))
        return;
      permissions.field.onChange(
        selected
          ? [...new Set([...permissions.field.value, permission])]
          : permissions.field.value.filter((value) => value !== permission),
      );
    },
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
