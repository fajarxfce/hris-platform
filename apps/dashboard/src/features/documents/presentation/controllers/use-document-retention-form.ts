import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { DocumentRetentionPolicyFields } from "../../domain/entities/document-retention-policy";
import type { DocumentRetentionEditorState } from "../models/document-retention-editor-state";
import type { DocumentRetentionEditorController } from "./document-retention-editor-controller";

export function useDocumentRetentionForm(
  controller: DocumentRetentionEditorController,
  state: DocumentRetentionEditorState,
) {
  const form = useForm<DocumentRetentionPolicyFields>({
    defaultValues: { retentionDays: "", reason: "" },
  });
  const { reset } = form;
  // Only a fresh review initializes fields; hiding an unauthorized review never replaces a pending command's draft.
  useEffect(() => {
    if (state.selection)
      reset({ retentionDays: state.selection.policy?.retentionDays?.toString() ?? "", reason: "" });
  }, [state.selection, reset]);
  const retentionDays = useController({ name: "retentionDays", control: form.control }),
    reason = useController({ name: "reason", control: form.control });
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
    retentionDays: retentionDays.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit(controller.save),
    refresh: () =>
      depart(() => {
        reset();
        void controller.refresh();
      }),
  };
}
