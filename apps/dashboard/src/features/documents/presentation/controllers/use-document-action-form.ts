import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { DocumentActionState } from "../models/document-action-state";
import type { DocumentActionController } from "./document-action-controller";

export function useDocumentActionForm(
  controller: DocumentActionController,
  state: DocumentActionState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({ name: "reason", control: form.control });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    editable: state.stage === "reviewing",
    submit: form.handleSubmit((fields) => controller.confirm(fields.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
