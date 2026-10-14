import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { TaxOpeningCommandState } from "../models/tax-opening-command-state";
import {
  type TaxOpeningFormValues,
  taxOpeningFields,
  taxOpeningFormValues,
} from "../models/tax-opening-form-values";
import type { TaxOpeningEditorController } from "./tax-opening-editor-controller";
export function useTaxOpeningForm(
  controller: TaxOpeningEditorController,
  state: TaxOpeningCommandState,
) {
  const values = useMemo(() => taxOpeningFormValues(state.context), [state.context]);
  const form = useForm<TaxOpeningFormValues>({ values });
  const throughMonth = useController({ control: form.control, name: "throughMonth" }).field;
  const residency = useController({ control: form.control, name: "residency" }).field;
  const ptkp = useController({ control: form.control, name: "ptkp" }).field;
  const reference = useController({ control: form.control, name: "reference" }).field;
  const reason = useController({ control: form.control, name: "reason" }).field;
  const employmentMonths = useController({ control: form.control, name: "employmentMonths" }).field;
  const taxableGross = useController({ control: form.control, name: "taxableGross" }).field;
  const retirementContributions = useController({
    control: form.control,
    name: "retirementContributions",
  }).field;
  const qualifiedDonations = useController({
    control: form.control,
    name: "qualifiedDonations",
  }).field;
  const withheld = useController({ control: form.control, name: "withheld" }).field;
  const previousEmployerNet = useController({
    control: form.control,
    name: "previousEmployerNet",
  }).field;
  const previousEmployerWithheld = useController({
    control: form.control,
    name: "previousEmployerWithheld",
  }).field;
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
    throughMonth,
    residency,
    ptkp,
    reference,
    reason,
    employmentMonths,
    taxableGross,
    retirementContributions,
    qualifiedDonations,
    withheld,
    previousEmployerNet,
    previousEmployerWithheld,
    editable,
    submit: form.handleSubmit((values) => controller.save(taxOpeningFields(values))),
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
  };
}
