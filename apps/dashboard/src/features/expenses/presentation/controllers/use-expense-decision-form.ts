import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import {
  expenseDecisionNeedsAcknowledgement,
  expenseDecisionNeedsReason,
} from "../../domain/policies/expense-decision-policy";
import type { ExpenseDecisionState } from "../models/expense-decision-state";
import type { ExpenseDecisionController } from "./expense-decision-controller";

export function useExpenseDecisionForm(
  controller: ExpenseDecisionController,
  state: ExpenseDecisionState,
) {
  const duplicateCount = state.review?.duplicateReceiptDigests.length ?? 0;
  const required = expenseDecisionNeedsReason(controller.decision, duplicateCount);
  const acknowledgeRequired = expenseDecisionNeedsAcknowledgement(
    controller.decision,
    duplicateCount,
  );
  const form = useForm({ defaultValues: { reason: "", acknowledgeDuplicates: false } });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { maxLength: 1000, validate: (value) => !required || value.trim().length > 0 },
  });
  const acknowledge = useController({
    name: "acknowledgeDuplicates",
    control: form.control,
    rules: { validate: (value) => !acknowledgeRequired || value },
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
    acknowledge: acknowledge.field,
    reasonInvalid: Boolean(reason.fieldState.error),
    acknowledgeInvalid: Boolean(acknowledge.fieldState.error),
    required,
    acknowledgeRequired,
    editable: state.stage === "reviewing",
    submit: form.handleSubmit((fields) =>
      controller.confirm(fields.reason, fields.acknowledgeDuplicates),
    ),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
