import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { validAccountEmail } from "../../domain/policies/account-invitation-policy";
import type { AccountInvitationState } from "../models/account-invitation-state";
import type {
  AccountInvitationController,
  AccountInvitationFields,
} from "./account-invitation-controller";
export function useAccountInvitationForm(
  controller: AccountInvitationController,
  state: AccountInvitationState,
) {
  const values = useMemo(
    () => ({
      email: state.review?.account.email ?? "",
      displayName: state.review?.account.displayName ?? "",
      reason: "",
    }),
    [state.review],
  );
  const form = useForm<AccountInvitationFields>({ values });
  const email = useController({
    name: "email",
    control: form.control,
    rules: { validate: (value) => validAccountEmail(value.trim()), required: true },
  });
  const displayName = useController({
    name: "displayName",
    control: form.control,
    rules: { required: true, maxLength: 200, validate: (value) => value.trim().length > 0 },
  });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { required: true, maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
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
    email: email.field,
    emailInvalid: email.fieldState.invalid,
    displayName: displayName.field,
    nameInvalid: displayName.fieldState.invalid,
    reason: reason.field,
    reasonInvalid: reason.fieldState.invalid,
    editable: state.stage === "editing",
    submit: form.handleSubmit((fields) => controller.save(fields)),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
