import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { DocumentUploadFields } from "../../domain/entities/document-upload";
import type { DocumentUploadState } from "../models/document-upload-state";
import type { DocumentUploadController } from "./document-upload-controller";

export function useDocumentUploadForm(
  controller: DocumentUploadController,
  state: DocumentUploadState,
) {
  const form = useForm<DocumentUploadFields>({
    defaultValues: { title: "", classification: "PERSONAL", reason: "" },
  });
  const title = useController({ name: "title", control: form.control });
  const classification = useController({ name: "classification", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "uploading"
      ? "pending"
      : state.stage === "paused"
        ? "unconfirmed"
        : state.stage !== "complete" &&
            (form.formState.isDirty || state.file !== null || state.stage === "selecting")
          ? "dirty"
          : "none",
  );
  return {
    title: title.field,
    classification: classification.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((fields) => controller.confirm(fields)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
