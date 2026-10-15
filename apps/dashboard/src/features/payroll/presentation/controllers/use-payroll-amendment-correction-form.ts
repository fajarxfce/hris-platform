import { useEffect, useMemo, useState } from "react";
import { useController, useForm } from "react-hook-form";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentCorrection } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentBasis } from "../../domain/entities/payroll-amendment-preparation";
import {
  compensationFields,
  compensationValuesFromTerms,
} from "../models/compensation-form-values";
import {
  payrollInputFields,
  payrollInputValuesFromTerms,
} from "../models/payroll-input-form-values";
import { useCompensationTermFields } from "./use-compensation-term-fields";
import { usePayrollInputTermFields } from "./use-payroll-input-term-fields";
export function usePayrollAmendmentCorrectionForm(
  source: PayrollAmendmentBasis,
  correction: PayrollAmendmentCorrection | null,
  onApply: (input: PayrollAmendmentCorrection) => void,
  onClose: () => void,
  onDirtyChange: (dirty: boolean) => void,
) {
  const compValues = useMemo(
    () =>
      compensationValuesFromTerms(correction?.compensation ?? source.compensation, source.month),
    [correction, source],
  );
  const inputValues = useMemo(
    () => payrollInputValuesFromTerms(correction?.input ?? source.input),
    [correction, source],
  );
  const compensation = useForm({ defaultValues: compValues }),
    input = useForm({ defaultValues: inputValues });
  const choices = useForm({
    defaultValues: {
      compensation: correction ? correction.compensation !== null : true,
      input: correction?.input != null,
    },
  });
  const withCompensation = useController({ control: choices.control, name: "compensation" }).field;
  const withInput = useController({ control: choices.control, name: "input" }).field;
  const [validation, setValidation] = useState<{
    kind: "compensation" | "input" | null;
    failure: Failure;
  } | null>(null);
  const [discardRequested, setDiscardRequested] = useState(false);
  const dirty =
    compensation.formState.isDirty || input.formState.isDirty || choices.formState.isDirty;
  useEffect(() => {
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);
  return {
    compensation: useCompensationTermFields(compensation, withCompensation.value),
    input: usePayrollInputTermFields(input, withInput.value),
    withCompensation: withCompensation.value,
    withInput: withInput.value,
    changeCompensation: (checked: boolean) => withCompensation.onChange(checked),
    changeInput: (checked: boolean) => withInput.onChange(checked),
    failure: validation?.failure ?? null,
    compensationFailure: validation?.kind === "compensation" ? validation.failure : null,
    inputFailure: validation?.kind === "input" ? validation.failure : null,
    discardRequested,
    keepEditing: () => setDiscardRequested(false),
    discard: onClose,
    close: () => {
      if (dirty) setDiscardRequested(true);
      else onClose();
    },
    submit: choices.handleSubmit((selection) => {
      const comp = selection.compensation ? compensationFields(compensation.getValues()) : null;
      const monthInput = selection.input ? payrollInputFields(input.getValues()) : null;
      if (comp && !comp.ok) {
        setValidation({ kind: "compensation", failure: comp.failure });
        return;
      }
      if (monthInput && !monthInput.ok) {
        setValidation({ kind: "input", failure: monthInput.failure });
        return;
      }
      if (!comp && !monthInput) {
        setValidation({
          kind: null,
          failure: { code: "invalid_payroll_amendment_corrections", fields: {}, parameters: {} },
        });
        return;
      }
      onApply({
        assessmentId: source.source.assessmentId,
        compensation: comp?.ok ? comp.value.terms : null,
        input: monthInput?.ok ? monthInput.value.terms : null,
      });
    }),
  };
}
