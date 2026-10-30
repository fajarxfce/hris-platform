import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { DocumentMetadataFields } from "../../domain/entities/document-metadata";
import type { DocumentMetadataEditorState } from "../models/document-metadata-editor-state";
import type { DocumentMetadataEditorController } from "./document-metadata-editor-controller";

export function useDocumentMetadataForm(
  controller: DocumentMetadataEditorController,
  state: DocumentMetadataEditorState,
) {
  const form = useForm<DocumentMetadataFields>({
    defaultValues: {
      category: "OTHER",
      identityNumber: "",
      issuingCountry: "",
      issuedOn: "",
      validUntil: "",
      reason: "",
    },
  });
  const { reset } = form;
  useEffect(() => {
    const value = state.selection?.metadata;
    if (value)
      reset({
        category: value.category,
        identityNumber: value.identityNumber ?? "",
        issuingCountry: value.issuingCountry ?? "",
        issuedOn: value.issuedOn ?? "",
        validUntil: value.validUntil ?? "",
        reason: "",
      });
  }, [state.selection, reset]);
  const category = useController({ name: "category", control: form.control });
  const identityNumber = useController({ name: "identityNumber", control: form.control });
  const issuingCountry = useController({ name: "issuingCountry", control: form.control });
  const issuedOn = useController({ name: "issuedOn", control: form.control });
  const validUntil = useController({ name: "validUntil", control: form.control });
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
    category: category.field,
    identityNumber: identityNumber.field,
    issuingCountry: issuingCountry.field,
    issuedOn: issuedOn.field,
    validUntil: validUntil.field,
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
