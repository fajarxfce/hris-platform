import { useEffect, useRef } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import {
  validatePayrollPeriodFields,
  validPayrollMonth,
} from "../../domain/policies/payroll-period-policy";
import type { PayrollPeriodCreationState } from "../models/payroll-period-creation-state";
import type { PayrollPeriodCreationController } from "./payroll-period-creation-controller";
export function usePayrollPeriodCreationForm(
  controller: PayrollPeriodCreationController,
  state: PayrollPeriodCreationState,
  month: string,
) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (state.stage !== "reviewing") return;
    headingRef.current?.focus({ preventScroll: true });
    headingRef.current?.scrollIntoView({ block: "center", behavior: "instant" });
  }, [state.stage]);
  const form = useForm({
    defaultValues: { earningsMonth: month, plannedPaymentDate: "", reason: "" },
  });
  const earningsMonth = useController({
      control: form.control,
      name: "earningsMonth",
      rules: { validate: validPayrollMonth },
    }),
    plannedPaymentDate = useController({
      control: form.control,
      name: "plannedPaymentDate",
      rules: {
        validate: (value) =>
          !validatePayrollPeriodFields({ ...form.getValues(), plannedPaymentDate: value })?.fields
            .plannedPaymentDate,
      },
    }),
    reason = useController({
      control: form.control,
      name: "reason",
      rules: { maxLength: 1000, validate: (value) => value.trim().length > 0 },
    });
  useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && (form.formState.isDirty || state.selected.length > 0)
          ? "dirty"
          : "none",
  );
  return {
    headingRef,
    earningsMonth: earningsMonth.field,
    plannedPaymentDate: plannedPaymentDate.field,
    reason: reason.field,
    invalidMonth: !!earningsMonth.fieldState.error,
    invalidPayment: !!plannedPaymentDate.fieldState.error,
    invalidReason: !!reason.fieldState.error,
    submit: form.handleSubmit(controller.review),
  };
}
