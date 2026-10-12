import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { WorkPeriodActionState } from "../models/work-period-action-state";
import type { WorkPeriodActionController } from "./work-period-action-controller";

export function useWorkPeriodActionForm(
  controller: WorkPeriodActionController,
  state: WorkPeriodActionState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { validate: (value) => value.trim().length > 0 && value.length <= 1000 },
  });
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
    invalidReason: Boolean(reason.fieldState.error),
    editable: state.stage === "reviewing",
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) => controller.confirm(fields.reason)),
  };
}
