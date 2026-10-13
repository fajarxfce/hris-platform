import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ExpenseDraftEditorState } from "../models/expense-draft-editor-state";
import type { ExpenseDraftEditorController } from "./expense-draft-editor-controller";
export function useExpenseDraftForm(
  controller: ExpenseDraftEditorController,
  state: ExpenseDraftEditorState,
) {
  const values = useMemo(
    () => ({
      title: state.original?.claim.draft.title ?? "",
      description: state.original?.claim.draft.description ?? "",
      reason: "",
    }),
    [state.original],
  );
  const form = useForm({ values });
  const title = useController({ name: "title", control: form.control }),
    description = useController({ name: "description", control: form.control }),
    reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" &&
            (form.formState.isDirty ||
              state.changed ||
              (state.panel.kind === "line" && state.panel.dirty))
          ? "dirty"
          : "none",
  );
  return {
    title: title.field,
    description: description.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit(controller.save),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
