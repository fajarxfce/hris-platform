import { useController, useForm } from "react-hook-form";
import { validAccountEmail } from "../../domain/policies/account-invitation-policy";
import type { PasswordRecoveryController } from "./password-recovery-controller";
export function usePasswordRecoveryForm(controller: PasswordRecoveryController) {
  const form = useForm({ defaultValues: { email: "" } });
  const email = useController({
    name: "email",
    control: form.control,
    rules: { required: true, validate: (value) => validAccountEmail(value.trim()) },
  });
  return {
    email: email.field,
    invalid: email.fieldState.invalid,
    submit: form.handleSubmit(async (value) => {
      await controller.request(value.email);
      if (controller.getSnapshot().stage === "sent") form.reset();
    }),
  };
}
