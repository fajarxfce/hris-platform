import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollPolicyEditorState } from "../models/payroll-policy-editor-state";
import {
  type PayrollPolicyFormValues,
  payrollPolicyFields,
  payrollPolicyFormValues,
} from "../models/payroll-policy-form-values";
import type { PayrollPolicyEditorController } from "./payroll-policy-editor-controller";

export function usePayrollPolicyForm(
  controller: PayrollPolicyEditorController,
  state: PayrollPolicyEditorState,
  month: string,
) {
  const values = useMemo(
    () => payrollPolicyFormValues(state.context, month),
    [state.context, month],
  );
  const form = useForm<PayrollPolicyFormValues>({ values });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control });
  const effectiveUntil = useController({ name: "effectiveUntil", control: form.control });
  const incomeTaxRuleId = useController({ name: "incomeTaxRuleId", control: form.control });
  const insuranceRuleId = useController({ name: "insuranceRuleId", control: form.control });
  const minimumMonthlyWage = useController({ name: "minimumMonthlyWage", control: form.control });
  const healthWageCap = useController({ name: "healthWageCap", control: form.control });
  const pensionWageCap = useController({ name: "pensionWageCap", control: form.control });
  const contributionRounding = useController({
    name: "contributionRounding",
    control: form.control,
  });
  const references = useController({ name: "references", control: form.control });
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
    effectiveFrom: effectiveFrom.field,
    effectiveUntil: effectiveUntil.field,
    incomeTaxRuleId: incomeTaxRuleId.field,
    insuranceRuleId: insuranceRuleId.field,
    minimumMonthlyWage: minimumMonthlyWage.field,
    healthWageCap: healthWageCap.field,
    pensionWageCap: pensionWageCap.field,
    contributionRounding: contributionRounding.field,
    references: references.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((input) => controller.save(payrollPolicyFields(input))),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
