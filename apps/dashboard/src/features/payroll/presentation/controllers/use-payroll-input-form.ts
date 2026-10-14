import { useMemo } from "react";
import { useController, useFieldArray, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { PayrollInputCommandState } from "../models/payroll-input-command-state";
import {
  type PayrollInputFormValues,
  payrollInputFields,
  payrollInputFormValues,
} from "../models/payroll-input-form-values";
import type { PayrollInputEditorController } from "./payroll-input-editor-controller";
export function usePayrollInputForm(
  controller: PayrollInputEditorController,
  state: PayrollInputCommandState,
) {
  const values = useMemo(() => payrollInputFormValues(state.context), [state.context]);
  const form = useForm<PayrollInputFormValues>({ values });
  const reason = useController({ control: form.control, name: "reason" }).field;
  const nonCashTaxable = useController({ control: form.control, name: "nonCashTaxable" }).field;
  const scheduledMonthUnits = useController({
    control: form.control,
    name: "scheduledMonthUnits",
  }).field;
  const reviewReference = useController({ control: form.control, name: "reviewReference" }).field;
  const holidayEnabled = useController({ control: form.control, name: "holidayEnabled" }).field;
  const holidayKind = useController({ control: form.control, name: "holidayKind" }).field;
  const holidayDate = useController({ control: form.control, name: "holidayDate" }).field;
  const serviceFrom = useController({ control: form.control, name: "serviceFrom" }).field;
  const priorPayment = useController({ control: form.control, name: "priorPayment" }).field;
  const holidayReference = useController({ control: form.control, name: "holidayReference" }).field;
  const promisedAmount = useController({ control: form.control, name: "promisedAmount" }).field;
  const earnings = useFieldArray({ control: form.control, name: "earnings" }),
    deductions = useFieldArray({ control: form.control, name: "deductions" }),
    days = useFieldArray({ control: form.control, name: "dayResolutions" });
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
    reason,
    nonCashTaxable,
    scheduledMonthUnits,
    reviewReference,
    holidayEnabled,
    holidayKind,
    holidayDate,
    serviceFrom,
    priorPayment,
    holidayReference,
    promisedAmount,
    editable,
    control: form.control,
    earnings: earnings.fields,
    deductions: deductions.fields,
    days: days.fields,
    addEarning: () => {
      if (editable && form.getValues("earnings").length < 40)
        earnings.append({ code: "", name: "", amount: "", taxable: true });
    },
    addDeduction: () => {
      if (editable && form.getValues("deductions").length < 20)
        deductions.append({ code: "", name: "", amount: "" });
    },
    addDay: () => {
      if (editable && form.getValues("dayResolutions").length < 62)
        days.append({ workDate: "", portion: "", disposition: "", reference: "" });
    },
    removeEarning: (index: number) => {
      if (
        editable &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < form.getValues("earnings").length
      )
        earnings.remove(index);
    },
    removeDeduction: (index: number) => {
      if (
        editable &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < form.getValues("deductions").length
      )
        deductions.remove(index);
    },
    removeDay: (index: number) => {
      if (
        editable &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < form.getValues("dayResolutions").length
      )
        days.remove(index);
    },
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
