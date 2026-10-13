import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ExpenseClaimActionState } from "../models/expense-claim-action-state";
import type { ExpenseClaimActionController } from "./expense-claim-action-controller";
export function useExpenseClaimActionForm(
  controller: ExpenseClaimActionController,
  state: ExpenseClaimActionState,
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
        : state.stage !== "saved" && form.formState.isDirty
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
