import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeaveBalanceAdjustmentState } from "../models/leave-balance-adjustment-state";
import type { LeaveBalanceAdjustmentController } from "./leave-balance-adjustment-controller";

export function useLeaveBalanceAdjustmentForm(
  controller: LeaveBalanceAdjustmentController,
  state: LeaveBalanceAdjustmentState,
) {
  const form = useForm({ defaultValues: { days: "", reason: "" } });
  const days = useController({ name: "days", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
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
    days: days.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((fields) => controller.save(fields.days, fields.reason)),
    // Reviewing the same target again preserves the draft and requires a new explicit save.
    refresh: controller.refresh,
  };
}
