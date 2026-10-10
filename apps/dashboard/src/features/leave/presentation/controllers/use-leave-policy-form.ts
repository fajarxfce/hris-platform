import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeavePolicyEditorState } from "../models/leave-policy-editor-state";
import {
  type LeavePolicyFormValues,
  leavePolicyFields,
  leavePolicyFormValues,
} from "../models/leave-policy-form-values";
import type { LeavePolicyEditorController } from "./leave-policy-editor-controller";

export function useLeavePolicyForm(
  controller: LeavePolicyEditorController,
  state: LeavePolicyEditorState,
  today: string,
) {
  const values = useMemo(() => leavePolicyFormValues(state.policy, today), [state.policy, today]);
  const form = useForm<LeavePolicyFormValues>({ values });
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control });
  const paid = useController({ name: "paid", control: form.control });
  const allowPartialDays = useController({ name: "allowPartialDays", control: form.control });
  const minServiceMonths = useController({ name: "minServiceMonths", control: form.control });
  const maxRequestDays = useController({ name: "maxRequestDays", control: form.control });
  const permanent = useController({ name: "permanent", control: form.control });
  const fixedTerm = useController({ name: "fixedTerm", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const attachmentRequired = useController({ name: "attachmentRequired", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const frequency = useController({ name: "frequency", control: form.control });
  const daysPerPeriod = useController({ name: "daysPerPeriod", control: form.control });
  const carryLimitDays = useController({ name: "carryLimitDays", control: form.control });
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
    code: code.field,
    name: name.field,
    effectiveFrom: effectiveFrom.field,
    paid: paid.field,
    allowPartialDays: allowPartialDays.field,
    minServiceMonths: minServiceMonths.field,
    maxRequestDays: maxRequestDays.field,
    permanent: permanent.field,
    fixedTerm: fixedTerm.field,
    active: active.field,
    attachmentRequired: attachmentRequired.field,
    reason: reason.field,
    frequency: frequency.field,
    daysPerPeriod: daysPerPeriod.field,
    carryLimitDays: carryLimitDays.field,
    editable: state.stage === "editing",
    submit: form.handleSubmit((input) => controller.save(leavePolicyFields(input))),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
