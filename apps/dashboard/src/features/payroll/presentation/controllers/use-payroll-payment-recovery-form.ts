import { useController, useForm } from "react-hook-form";
import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollPaymentRecoveryState } from "../models/payroll-payment-recovery-state";
import type { PayrollPaymentRecoveryController } from "./payroll-payment-recovery-controller";

export function usePayrollPaymentRecoveryForm(
  controller: PayrollPaymentRecoveryController,
  state: PayrollPaymentRecoveryState,
  timezone: string,
) {
  const form = useForm({
    defaultValues: { amount: "", transactionReference: "", occurredAt: "", reason: "" },
  });
  const amount = useController({
    name: "amount",
    control: form.control,
    rules: { required: true, maxLength: 15 },
  });
  const reference = useController({
    name: "transactionReference",
    control: form.control,
    rules: { required: true, maxLength: 100 },
  });
  const occurredAt = useController({
    name: "occurredAt",
    control: form.control,
    rules: { validate: (value) => parseCompanyDateTime(value, timezone) !== null },
  });
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
    amount: amount.field,
    reference: reference.field,
    occurredAt: occurredAt.field,
    reason: reason.field,
    invalid: Object.keys(form.formState.errors).length > 0,
    editable: state.stage === "ready" && state.review?.canConfirm === true,
    submit: form.handleSubmit((fields) => {
      const at = parseCompanyDateTime(fields.occurredAt, timezone);
      if (at === null) {
        form.setError("occurredAt", { type: "invalid_date_time" });
        return;
      }
      return controller.confirm({ ...fields, occurredAt: at });
    }),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
