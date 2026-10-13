import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ExpensePolicyEditorState } from "../models/expense-policy-editor-state";
import {
  type ExpensePolicyFormValues,
  expensePolicyFields,
  expensePolicyFormValues,
} from "../models/expense-policy-form-values";
import type { ExpensePolicyEditorController } from "./expense-policy-editor-controller";

export function useExpensePolicyForm(
  controller: ExpensePolicyEditorController,
  state: ExpensePolicyEditorState,
  today: string,
) {
  const values = useMemo(() => expensePolicyFormValues(state.policy, today), [state.policy, today]);
  const form = useForm<ExpensePolicyFormValues>({ values });
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control });
  const maximumLineAmount = useController({ name: "maximumLineAmount", control: form.control });
  const maximumClaimAmount = useController({ name: "maximumClaimAmount", control: form.control });
  const maximumAgeDays = useController({ name: "maximumAgeDays", control: form.control });
  const receiptRequired = useController({ name: "receiptRequired", control: form.control });
  const costCenterRequired = useController({ name: "costCenterRequired", control: form.control });
  const permanent = useController({ name: "permanent", control: form.control });
  const fixedTerm = useController({ name: "fixedTerm", control: form.control });
  const active = useController({ name: "active", control: form.control });
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
    code: code.field,
    name: name.field,
    effectiveFrom: effectiveFrom.field,
    maximumLineAmount: maximumLineAmount.field,
    maximumClaimAmount: maximumClaimAmount.field,
    maximumAgeDays: maximumAgeDays.field,
    receiptRequired: receiptRequired.field,
    costCenterRequired: costCenterRequired.field,
    permanent: permanent.field,
    fixedTerm: fixedTerm.field,
    active: active.field,
    reason: reason.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((input) => controller.save(expensePolicyFields(input))),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
