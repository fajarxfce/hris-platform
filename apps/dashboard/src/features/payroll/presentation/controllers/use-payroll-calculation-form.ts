import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollCalculationReview } from "../../domain/entities/payroll-calculation-review";
import type { PayrollCalculationCommandState } from "../models/payroll-calculation-command-state";
import type { PayrollCalculationStartController } from "./payroll-calculation-start-controller";
export function usePayrollCalculationForm(
  controller: PayrollCalculationStartController,
  state: PayrollCalculationCommandState<PayrollCalculationReview>,
) {
  const form = useForm({ defaultValues: { incomeDueDate: "", reviewReference: "", reason: "" } });
  const due = useController({
      control: form.control,
      name: "incomeDueDate",
      rules: { required: true },
    }),
    reference = useController({
      control: form.control,
      name: "reviewReference",
      rules: { maxLength: 200, validate: (value) => value.trim().length > 0 },
    }),
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
    due: due.field,
    reference: reference.field,
    reason: reason.field,
    invalidDue: !!due.fieldState.error || !!state.failure?.fields.incomeDueDate,
    invalidReference: !!reference.fieldState.error || !!state.failure?.fields.reviewReference,
    invalidReason: !!reason.fieldState.error || !!state.failure?.fields.reason,
    submit: form.handleSubmit(controller.save),
    reload: controller.refresh,
  };
}
