import { useLayoutEffect } from "react";
import { useController, useForm } from "react-hook-form";
import type { ExpensePaymentOutcome } from "../../domain/entities/expense-payment-outcome";
import { expenseOutcomeValues, parseExpenseOutcomeValues } from "../models/expense-outcome-values";
import type { ExpenseReconciliationController } from "./expense-reconciliation-controller";
export function useExpenseOutcomeForm(
  controller: ExpenseReconciliationController,
  itemId: string,
  original: ExpensePaymentOutcome | null,
  timezone: string,
) {
  const form = useForm({ defaultValues: expenseOutcomeValues(original, timezone) });
  const status = useController({
    name: "status",
    control: form.control,
    rules: { required: true },
  });
  const reference = useController({
    name: "reference",
    control: form.control,
    rules: {
      maxLength: 100,
      validate: (value) =>
        status.field.value !== "SUCCEEDED" ||
        /^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u.test(value.trim()),
    },
  });
  const occurredAt = useController({
    name: "occurredAt",
    control: form.control,
    rules: { required: true },
  });
  const reason = useController({
    name: "reason",
    control: form.control,
    rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
  });
  const confirmed = useController({
    name: "confirmedNoTransfer",
    control: form.control,
    rules: { validate: (value) => status.field.value !== "FAILED" || value },
  });
  useLayoutEffect(() => {
    controller.setEntryDirty(itemId, form.formState.isDirty);
  }, [controller, itemId, form.formState.isDirty]);
  return {
    status: status.field,
    reference: reference.field,
    occurredAt: occurredAt.field,
    reason: reason.field,
    confirmed: confirmed.field,
    invalid: Object.keys(form.formState.errors).length > 0,
    submit: form.handleSubmit((fields) => {
      const outcome = parseExpenseOutcomeValues(fields, itemId, timezone, original);
      if (outcome) controller.saveOutcome(outcome);
      else form.setError("root", { type: "invalid_expense_payment_result" });
    }),
    cancel: () => controller.selectItem(null),
    clear: controller.clearOutcome,
  };
}
