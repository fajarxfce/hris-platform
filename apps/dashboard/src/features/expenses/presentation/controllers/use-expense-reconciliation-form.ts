import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ExpenseReconciliationState } from "../models/expense-reconciliation-state";
import type { ExpenseReconciliationController } from "./expense-reconciliation-controller";
export function useExpenseReconciliationForm(
  controller: ExpenseReconciliationController,
  state: ExpenseReconciliationState,
) {
  const form = useForm({ defaultValues: { reason: "" } });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" &&
            (form.formState.isDirty || state.results.length > 0 || state.entryDirty)
          ? "dirty"
          : "none",
  );
  return {
    reason: reason.field,
    reasonInvalid: Boolean(reason.fieldState.error),
    editable: state.stage === "reviewing",
    submit: form.handleSubmit((fields) => controller.confirm(fields.reason)),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
