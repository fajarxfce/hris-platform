import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleTaskEditorState } from "../models/lifecycle-task-editor-state";
import type {
  LifecycleTaskEditorController,
  LifecycleTaskFields,
} from "./lifecycle-task-editor-controller";

export function useLifecycleTaskForm(
  controller: LifecycleTaskEditorController,
  state: LifecycleTaskEditorState,
  onClose: () => void,
  onReload: () => void,
  onAssign: () => void,
) {
  const form = useForm<LifecycleTaskFields>({
    defaultValues: { status: controller.statuses[0] ?? "DONE", reason: "" },
  });
  const status = useController({ name: "status", control: form.control });
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
    status: status.field,
    reason: reason.field,
    editable: state.stage === "editing" && controller.statuses.length > 0,
    submit: form.handleSubmit(controller.save),
    close: () => depart(onClose),
    assign: () => depart(onAssign),
    reload: () => depart(onReload),
  };
}
