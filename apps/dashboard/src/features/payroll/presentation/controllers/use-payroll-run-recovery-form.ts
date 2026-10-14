import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import type { PayrollCalculationCommandState } from "../models/payroll-calculation-command-state";
import type { PayrollRunRecoveryController } from "./payroll-run-recovery-controller";
export function usePayrollRunRecoveryForm(
  controller: PayrollRunRecoveryController,
  state: PayrollCalculationCommandState<PayrollRunDetails>,
) {
  const form = useForm({ defaultValues: { reason: "" } }),
    reason = useController({
      control: form.control,
      name: "reason",
      rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
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
