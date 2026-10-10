import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleCaseActionState } from "../models/lifecycle-case-action-state";
import type { LifecycleCaseActionController } from "./lifecycle-case-action-controller";

export function useLifecycleCaseActionForm(
  controller: LifecycleCaseActionController,
  state: LifecycleCaseActionState,
  onClose: () => void,
  onReload: () => void,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({ name: "reason", control: form.control });
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
    editable: state.stage === "editing" && controller.allowed,
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    close: () => depart(onClose),
    reload: () => depart(onReload),
  };
}
