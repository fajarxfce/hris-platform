import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollPeriodCancellationState } from "../models/payroll-period-cancellation-state";
import type { PayrollPeriodCancellationController } from "./payroll-period-cancellation-controller";
export function usePayrollPeriodCancellationForm(
  controller: PayrollPeriodCancellationController,
  state: PayrollPeriodCancellationState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({
    control: form.control,
    name: "reason",
    rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
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
    reason: reason.field,
    invalidReason: !!reason.fieldState.error,
    submit: form.handleSubmit((values) => controller.save(values.reason)),
    reload: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
