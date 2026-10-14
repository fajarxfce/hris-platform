import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollReviewCommandState } from "../models/payroll-review-command-state";
export function usePayrollReviewReasonForm(
  controller: Readonly<{ save: (reason: string) => Promise<void>; refresh: () => Promise<void> }>,
  state: PayrollReviewCommandState<unknown>,
  required = true,
) {
  const form = useForm({ defaultValues: { reason: "" } }),
    reason = useController({
      control: form.control,
      name: "reason",
      rules: { maxLength: 1000, validate: (value) => !required || value.trim().length > 0 },
    });
  useNavigationProtection(
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
    reload: controller.refresh,
  };
}
