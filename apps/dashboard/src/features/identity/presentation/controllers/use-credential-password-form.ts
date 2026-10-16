import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import type { CredentialConfirmationController } from "./credential-confirmation-controller";
export function useCredentialPasswordForm(controller: CredentialConfirmationController) {
  const form = useForm({ defaultValues: { password: "", confirmation: "" } });
  const password = useController({
    name: "password",
    control: form.control,
    rules: { required: true, minLength: 12, maxLength: 128 },
  });
  const confirmation = useController({
    name: "confirmation",
    control: form.control,
    rules: { required: true, validate: (value) => value === form.getValues("password") },
  });
  const [visible, setVisible] = useReducer((_previous: boolean, next: boolean) => next, false);
  return {
    password: password.field,
    confirmation: confirmation.field,
    passwordInvalid: password.fieldState.invalid,
    confirmationInvalid: confirmation.fieldState.invalid,
    visible,
    toggleVisibility: () => setVisible(!visible),
    submit: form.handleSubmit(async (value) => {
      try {
        await controller.confirm(value.password);
      } finally {
        form.reset();
        setVisible(false);
      }
    }),
  };
}
