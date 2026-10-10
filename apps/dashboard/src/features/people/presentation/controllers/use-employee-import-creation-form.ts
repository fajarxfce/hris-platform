import { useEffect } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { EmployeeImportCreationState } from "../models/employee-import-creation-state";
import type { EmployeeImportCreationController } from "./employee-import-creation-controller";

export function useEmployeeImportCreationForm(
  controller: EmployeeImportCreationController,
  state: EmployeeImportCreationState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({ name: "reason", control: form.control });
  const reset = form.reset;
  useEffect(() => {
    if (state.stage === "saved") reset();
  }, [state.stage, reset]);
  useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && (form.formState.isDirty || state.file !== null)
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((fields) => controller.start(fields.reason)),
  };
}
