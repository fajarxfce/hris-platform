import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollCommandState } from "../models/payroll-command-state";
export function usePayrollReasonForm(
  controller: Readonly<{ save: (reason: string) => Promise<void>; refresh: () => Promise<void> }>,
  state: PayrollCommandState<unknown>,
  required = true,
) {
  const form = useForm({ defaultValues: { reason: "" } }),
    reason = useController({
      control: form.control,
      name: "reason",
      rules: { maxLength: 1000, validate: (value) => !required || value.trim().length > 0 },
    });
  useEffect(() => {
    if (state.review === null) form.reset({ reason: "" });
  }, [state.review, form.reset]);
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
    reason: reason.field,
    invalidReason: !!reason.fieldState.error || !!state.failure?.fields.reason,
    submit: form.handleSubmit((values) => controller.save(values.reason)),
    reload: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
