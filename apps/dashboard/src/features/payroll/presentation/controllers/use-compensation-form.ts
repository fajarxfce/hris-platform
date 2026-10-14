import { useMemo } from "react";
import { useController, useFieldArray, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { insurancePrograms as allInsurancePrograms } from "../../domain/entities/compensation-terms";
import type { CompensationEditorState } from "../models/compensation-editor-state";
import {
  type CompensationFormValues,
  compensationFields,
  compensationFormValues,
} from "../models/compensation-form-values";
import type { CompensationEditorController } from "./compensation-editor-controller";
export function useCompensationForm(
  controller: CompensationEditorController,
  state: CompensationEditorState,
  month: string,
) {
  const values = useMemo(
    () => compensationFormValues(state.context, month),
    [state.context, month],
  );
  const form = useForm<CompensationFormValues>({ values });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control }).field;
  const reason = useController({ name: "reason", control: form.control }).field;
  const basicSalary = useController({ name: "basicSalary", control: form.control }).field;
  const treatment = useController({ name: "treatment", control: form.control }).field;
  const residency = useController({ name: "residency", control: form.control }).field;
  const ptkp = useController({ name: "ptkp", control: form.control }).field;
  const residenceCountry = useController({ name: "residenceCountry", control: form.control }).field;
  const subjectiveFrom = useController({ name: "subjectiveFrom", control: form.control }).field;
  const subjectiveUntil = useController({ name: "subjectiveUntil", control: form.control }).field;
  const verifiedOn = useController({ name: "verifiedOn", control: form.control }).field;
  const verificationReference = useController({
    name: "verificationReference",
    control: form.control,
  }).field;
  const insuranceWage = useController({ name: "insuranceWage", control: form.control }).field;
  const insurancePrograms = useController({
    name: "insurancePrograms",
    control: form.control,
  }).field;
  const insuranceExemptionReason = useController({
    name: "insuranceExemptionReason",
    control: form.control,
  }).field;
  const accidentRisk = useController({ name: "accidentRisk", control: form.control }).field;
  const additionalHealthDependents = useController({
    name: "additionalHealthDependents",
    control: form.control,
  }).field;
  const additionalRetirementContribution = useController({
    name: "additionalRetirementContribution",
    control: form.control,
  }).field;
  const qualifiedDonation = useController({
    name: "qualifiedDonation",
    control: form.control,
  }).field;
  const otherNetDeduction = useController({
    name: "otherNetDeduction",
    control: form.control,
  }).field;
  const payBasisEnabled = useController({ name: "payBasisEnabled", control: form.control }).field;
  const proration = useController({ name: "proration", control: form.control }).field;
  const workWeek = useController({ name: "workWeek", control: form.control }).field;
  const shortestWorkDay = useController({ name: "shortestWorkDay", control: form.control }).field;
  const overtimeEligibility = useController({
    name: "overtimeEligibility",
    control: form.control,
  }).field;
  const overtimeExemptionReference = useController({
    name: "overtimeExemptionReference",
    control: form.control,
  }).field;
  const regularNonFixedWage = useController({
    name: "regularNonFixedWage",
    control: form.control,
  }).field;
  const serviceMonthConvention = useController({
    name: "serviceMonthConvention",
    control: form.control,
  }).field;
  const rounding = useController({ name: "rounding", control: form.control }).field;
  const reviewReference = useController({ name: "reviewReference", control: form.control }).field;
  const earnings = useFieldArray({ name: "fixedEarnings", control: form.control });
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
    effectiveFrom,
    reason,
    basicSalary,
    treatment,
    residency,
    ptkp,
    residenceCountry,
    subjectiveFrom,
    subjectiveUntil,
    verifiedOn,
    verificationReference,
    insuranceWage,
    insuranceExemptionReason,
    accidentRisk,
    additionalHealthDependents,
    additionalRetirementContribution,
    qualifiedDonation,
    otherNetDeduction,
    payBasisEnabled,
    proration,
    workWeek,
    shortestWorkDay,
    overtimeEligibility,
    overtimeExemptionReference,
    regularNonFixedWage,
    serviceMonthConvention,
    rounding,
    reviewReference,
    editable,
    control: form.control,
    earnings: earnings.fields,
    programs: allInsurancePrograms.map((program) => ({
      id: program,
      checked: insurancePrograms.value.includes(program),
      onChange: (_: unknown, data: { checked: boolean | "mixed" }) => {
        if (!editable || typeof data.checked !== "boolean") return;
        const current = form.getValues("insurancePrograms");
        insurancePrograms.onChange(
          data.checked
            ? [...new Set([...current, program])]
            : current.filter((value) => value !== program),
        );
      },
    })),
    showSubjectivePeriod: residency.value === "RESIDENT",
    showInsuranceExemption: insurancePrograms.value.length < 5,
    showShortestDay: workWeek.value === "SIX_DAYS",
    showOvertimeExemption: overtimeEligibility.value === "EXEMPT",
    changeWorkWeek: (value: string) => {
      if (!editable || (value !== "FIVE_DAYS" && value !== "SIX_DAYS")) return;
      workWeek.onChange(value);
      if (value === "FIVE_DAYS") shortestWorkDay.onChange("");
    },
    changeOvertimeEligibility: (value: string) => {
      if (!editable || (value !== "ELIGIBLE" && value !== "EXEMPT")) return;
      overtimeEligibility.onChange(value);
      if (value === "ELIGIBLE") overtimeExemptionReference.onChange("");
    },
    changeResidency: (value: string) => {
      if (!editable || (value !== "RESIDENT" && value !== "NON_RESIDENT")) return;
      residency.onChange(value);
      if (value === "NON_RESIDENT") {
        subjectiveFrom.onChange("");
        subjectiveUntil.onChange("");
      }
    },
    addEarning: () => {
      if (editable && form.getValues("fixedEarnings").length < 20)
        earnings.append({ code: "", name: "", amount: "", taxable: true });
    },
    removeEarning: (index: number) => {
      if (
        editable &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < form.getValues("fixedEarnings").length
      )
        earnings.remove(index);
    },
    submit: form.handleSubmit((values) => {
      const fields = compensationFields(values);
      if (fields.ok) return controller.save(fields.value);
      controller.reportValidationFailure(fields.failure);
    }),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
