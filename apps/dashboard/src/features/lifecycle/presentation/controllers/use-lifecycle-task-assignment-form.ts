import { useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleAssignee } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleTaskEditorState } from "../models/lifecycle-task-editor-state";
import type { LifecycleTaskAssignmentController } from "./lifecycle-task-assignment-controller";

type Fields = { member: LifecycleAssignee | null; unassign: boolean; reason: string };
export function useLifecycleTaskAssignmentForm(
  controller: LifecycleTaskAssignmentController,
  state: LifecycleTaskEditorState,
  onClose: () => void,
  onReload: () => void,
  onBack: () => void,
) {
  const form = useForm<Fields>({ defaultValues: { member: null, unassign: false, reason: "" } });
  const reason = useController({ name: "reason", control: form.control });
  const [member, unassign] = useWatch({ control: form.control, name: ["member", "unassign"] });
  const [picker, openPicker] = useReducer((_old: boolean, next: boolean) => next, false);
  const editable = state.stage === "editing" && controller.assignable;
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
    member,
    unassign,
    editable,
    picker,
    choose: () => {
      if (editable) openPicker(true);
    },
    closePicker: () => openPicker(false),
    select: (value: LifecycleAssignee) => {
      if (editable) {
        form.setValue("member", value, { shouldDirty: true });
        form.setValue("unassign", false, { shouldDirty: true });
      }
      openPicker(false);
    },
    remove: () => {
      if (editable) {
        form.setValue("member", null, { shouldDirty: true });
        form.setValue("unassign", true, { shouldDirty: true });
      }
    },
    submit: form.handleSubmit((fields) =>
      controller.save({
        assigneeId: fields.unassign ? null : (fields.member?.id ?? ""),
        reason: fields.reason,
      }),
    ),
    close: () => depart(onClose),
    reload: () => depart(onReload),
    back: () => depart(onBack),
  };
}
