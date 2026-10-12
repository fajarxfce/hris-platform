import { useController, useForm } from "react-hook-form";
import { isUuid } from "../../../../core/domain/identifiers";

export function useDocumentRetentionLookup(onOpen: (documentId: string) => void) {
  const form = useForm({ defaultValues: { documentId: "" } });
  const documentId = useController({
    name: "documentId",
    control: form.control,
    rules: { validate: (value) => isUuid(value.trim()) },
  });
  return {
    documentId: documentId.field,
    invalid: documentId.fieldState.invalid,
    submit: form.handleSubmit((input) => onOpen(input.documentId.trim().toLowerCase())),
  };
}
