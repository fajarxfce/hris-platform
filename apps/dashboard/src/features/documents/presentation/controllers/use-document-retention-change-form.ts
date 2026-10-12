import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { DocumentRetentionChangeState } from "../models/document-retention-change-state";
import type { DocumentRetentionChangeController } from "./document-retention-change-controller";

export function useDocumentRetentionChangeForm(
  controller: DocumentRetentionChangeController,
  state: DocumentRetentionChangeState,
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
    submit: form.handleSubmit((input) => controller.confirm(input.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
