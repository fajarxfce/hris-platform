import { useEffect, useState } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollAmendmentEditorState } from "../models/payroll-amendment-editor-state";
import type { PayrollAmendmentEditorController } from "./payroll-amendment-editor-controller";
export function usePayrollAmendmentEditorForm(
  controller: PayrollAmendmentEditorController,
  state: PayrollAmendmentEditorState,
) {
  const form = useForm({ defaultValues: { reason: "" } }),
    reason = useController({ control: form.control, name: "reason" }).field;
  const [monthDirty, setMonthDirty] = useState(false);
  useEffect(() => {
    if (state.context === null) form.reset({ reason: "" });
  }, [state.context, form.reset]);
  const depart = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && (state.dirty || form.formState.isDirty || monthDirty)
          ? "dirty"
          : "none",
  );
  return {
    reason,
    changeMonthDirty: setMonthDirty,
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
