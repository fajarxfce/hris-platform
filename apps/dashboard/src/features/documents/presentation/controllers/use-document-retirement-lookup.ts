import { useController, useForm } from "react-hook-form";
import { isUuid } from "../../../../core/domain/identifiers";

export function useDocumentRetirementLookup(onOpen: (revisionId: string) => void) {
  const form = useForm({ defaultValues: { revisionId: "" } });
  const revisionId = useController({
    name: "revisionId",
    control: form.control,
    rules: { validate: (value) => isUuid(value.trim()) },
  });
  return {
    revisionId: revisionId.field,
    invalid: revisionId.fieldState.invalid,
    submit: form.handleSubmit((input) => onOpen(input.revisionId.trim().toLowerCase())),
  };
}
