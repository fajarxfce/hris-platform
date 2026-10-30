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
    defaultValues: {
      title: "",
      classification: "PERSONAL",
      reason: "",
      metadata: {
        category: "OTHER",
        identityNumber: "",
        issuingCountry: "",
        issuedOn: "",
        validUntil: "",
      },
    },
  });
  const title = useController({ name: "title", control: form.control });
  const classification = useController({ name: "classification", control: form.control });
  const category = useController({ name: "metadata.category", control: form.control });
  const identityNumber = useController({ name: "metadata.identityNumber", control: form.control });
  const issuingCountry = useController({ name: "metadata.issuingCountry", control: form.control });
  const issuedOn = useController({ name: "metadata.issuedOn", control: form.control });
  const validUntil = useController({ name: "metadata.validUntil", control: form.control });
  const metadataSupported = state.review?.kind === "new" && state.review.metadataSupported === true;
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
    category: { ...category.field, value: category.field.value ?? "OTHER" },
    identityNumber: { ...identityNumber.field, value: identityNumber.field.value ?? "" },
    issuingCountry: { ...issuingCountry.field, value: issuingCountry.field.value ?? "" },
    issuedOn: { ...issuedOn.field, value: issuedOn.field.value ?? "" },
    validUntil: { ...validUntil.field, value: validUntil.field.value ?? "" },
    metadataSupported,
    title: title.field,
    classification: classification.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit(({ metadata, ...fields }) =>
      controller.confirm(metadataSupported && metadata ? { ...fields, metadata } : fields),
    ),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
