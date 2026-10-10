import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { EmploymentCancellationState } from "../models/employment-cancellation-state";
import type { EmploymentCancellationController } from "./employment-cancellation-controller";

export function useEmploymentCancellationForm(
  controller: EmploymentCancellationController,
  state: EmploymentCancellationState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reset = form.reset;
  useEffect(() => reset(state.details ? { reason: "" } : undefined), [state.details, reset]);
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "cancelled" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    editable: state.stage === "reviewing" && state.details?.canCancel === true,
    submit: form.handleSubmit((fields) => controller.cancelRevision(fields.reason)),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
