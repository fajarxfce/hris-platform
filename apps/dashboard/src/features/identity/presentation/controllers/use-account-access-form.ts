import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AccountAccessEditorState } from "../models/account-access-editor-state";
import {
  type AccountAccessFormValues,
  accountAccessFormValues,
} from "../models/account-access-form-values";
import { platformPermissionLabel } from "../models/managed-account-view";
import type { AccountAccessEditorController } from "./account-access-editor-controller";
export function useAccountAccessForm(
  controller: AccountAccessEditorController,
  state: AccountAccessEditorState,
  accountId: AccountId,
  locale: Locale,
) {
  const values = useMemo(() => accountAccessFormValues(state.review), [state.review]);
  const form = useForm<AccountAccessFormValues>({ values });
  const active = useController({ name: "active", control: form.control });
  const permissions = useController({ name: "platformPermissions", control: form.control });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: {
      required: true,
      maxLength: 1000,
      validate: (value) => value.trim().length > 0,
    },
  });
  const editable = state.stage === "editing";
  const ownAccount = state.review?.account.id === accountId.toLowerCase();
  const invitationPending = state.review?.account.invitationPending === true;
  const changedAccess =
    invitationPending ||
    active.field.value !== state.review?.account.active ||
    permissions.field.value.length !== state.review?.account.platformPermissions.length ||
    permissions.field.value.some(
      (value) => !state.review?.account.platformPermissions.includes(value),
    );
  const canSave = editable && changedAccess && (!invitationPending || !active.field.value);
  const options = useMemo(() => {
    const available = state.review?.assignablePermissions ?? [];
    return [...new Set([...available, ...(state.review?.account.platformPermissions ?? [])])].map(
      (value) => ({
        id: value,
        label: platformPermissionLabel(value, locale),
        selected: permissions.field.value.includes(value),
        disabled:
          !editable ||
          (ownAccount && value === "identity.manage") ||
          (!available.includes(value) && !permissions.field.value.includes(value)),
      }),
    );
  }, [state.review, permissions.field.value, editable, ownAccount, locale]);
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
    reasonInvalid: reason.fieldState.invalid,
    editable,
    ownAccount,
    invitationPending,
    changedAccess,
    options,
    activeDisabled: !editable || ownAccount || invitationPending,
    canSave,
    submit: form.handleSubmit((fields) => {
      if (canSave) return controller.save(fields);
    }),
    selectPermission: (permission: string, selected: boolean) => {
      if (
        !editable ||
        (ownAccount && permission === "identity.manage") ||
        (selected && !state.review?.assignablePermissions.includes(permission))
      )
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
