import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AttendanceReviewChange } from "../../domain/entities/attendance-review-change";
import type { AttendanceEditorState } from "../models/attendance-editor-state";
import type { AttendanceEditorController } from "./attendance-editor-controller";

export function useAttendanceReviewForm(
  controller: AttendanceEditorController,
  state: AttendanceEditorState,
) {
  const form = useForm<Pick<AttendanceReviewChange, "decision" | "reason">>({
    defaultValues: { decision: "ACCEPT", reason: "" },
  });
  const decision = useController({ name: "decision", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const requestDeparture = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    decision: decision.field,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    submit: form.handleSubmit(controller.review),
    refresh: () =>
      requestDeparture(() => {
        form.reset({ decision: "ACCEPT", reason: "" });
        void controller.refresh();
      }),
  };
}
