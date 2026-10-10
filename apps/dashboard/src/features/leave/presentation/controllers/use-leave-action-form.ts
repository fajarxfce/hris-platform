import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeaveActionState } from "../models/leave-action-state";
import type { LeaveActionController } from "./leave-action-controller";

export function useLeaveActionForm(controller: LeaveActionController, state: LeaveActionState) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    required: controller.intent !== "approve",
    editable: state.stage === "reviewing",
    submit: form.handleSubmit((fields) => controller.confirm(fields.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
