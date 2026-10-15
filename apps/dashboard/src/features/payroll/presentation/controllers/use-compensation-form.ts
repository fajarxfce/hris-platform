import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { CompensationEditorState } from "../models/compensation-editor-state";
import {
  type CompensationFormValues,
  compensationFields,
  compensationFormValues,
} from "../models/compensation-form-values";
import type { CompensationEditorController } from "./compensation-editor-controller";
import { useCompensationTermFields } from "./use-compensation-term-fields";
export function useCompensationForm(
  controller: CompensationEditorController,
  state: CompensationEditorState,
  month: string,
) {
  const values = useMemo(
    () => compensationFormValues(state.context, month),
    [state.context, month],
  );
  const form = useForm<CompensationFormValues>({ values });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control }).field;
  const reason = useController({ name: "reason", control: form.control }).field;
  const editable = state.stage === "editing";
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
    ...useCompensationTermFields(form, editable),
    effectiveFrom,
    reason,
    submit: form.handleSubmit((values) => {
      const fields = compensationFields(values);
      if (fields.ok) return controller.save(fields.value);
      controller.reportValidationFailure(fields.failure);
    }),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
