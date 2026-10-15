import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollInputCommandState } from "../models/payroll-input-command-state";
import {
  type PayrollInputFormValues,
  payrollInputFields,
  payrollInputFormValues,
} from "../models/payroll-input-form-values";
import type { PayrollInputEditorController } from "./payroll-input-editor-controller";
import { usePayrollInputTermFields } from "./use-payroll-input-term-fields";
export function usePayrollInputForm(
  controller: PayrollInputEditorController,
  state: PayrollInputCommandState,
) {
  const values = useMemo(() => payrollInputFormValues(state.context), [state.context]);
  const form = useForm<PayrollInputFormValues>({ values });
  const reason = useController({ control: form.control, name: "reason" }).field;
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
    ...usePayrollInputTermFields(form, editable),
    reason,
    submit: form.handleSubmit((values) => {
      const result = payrollInputFields(values);
      if (result.ok) return controller.save(result.value);
      controller.reportValidationFailure(result.failure);
    }),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
