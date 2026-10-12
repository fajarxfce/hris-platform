import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { isOvertimeWorkDate } from "../../domain/policies/overtime-planning-policy";
import type { OvertimePlanState } from "../models/overtime-plan-state";
import {
  emptyOvertimePlanValues,
  type OvertimePlanValues,
  parseOvertimePlanValues,
} from "../models/overtime-plan-values";
import type { OvertimePlanController } from "./overtime-plan-controller";

export function useOvertimePlanForm(
  controller: OvertimePlanController,
  state: OvertimePlanState,
  canCreate: boolean,
) {
  const form = useForm<OvertimePlanValues>({ defaultValues: emptyOvertimePlanValues() });
  const startsAt = useController({ name: "startsAt", control: form.control });
  const endsAt = useController({ name: "endsAt", control: form.control });
  const breakMinutes = useController({ name: "breakMinutes", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const [pickerOpen, setPicker] = useReducer((_old: boolean, next: boolean) => next, false);
  const depart = useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const selectionEnabled =
    canCreate && !["submitting", "unconfirmed", "saved"].includes(state.stage);
  return {
    startsAt: startsAt.field,
    endsAt: endsAt.field,
    breakMinutes: breakMinutes.field,
    reason: reason.field,
    editable: state.stage === "reviewing",
    selectionEnabled,
    canChooseEmployee: selectionEnabled && isOvertimeWorkDate(state.workDate),
    pickerOpen,
    invalidLocalTime: Boolean(form.formState.errors.root),
    changeDate: (_event: unknown, data: { value: string }) =>
      depart(() => {
        form.reset();
        setPicker(false);
        controller.changeDate(data.value);
      }),
    openPicker: () =>
      depart(() => {
        form.reset();
        setPicker(true);
      }),
    closePicker: () => setPicker(false),
    selectEmployee: (id: string) => {
      setPicker(false);
      void controller.selectEmployee(id);
    },
    refresh: () =>
      depart(() => {
        form.reset();
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) => {
      if (!state.review || state.stage !== "reviewing") return;
      const requested = parseOvertimePlanValues(fields, state.review.timezone);
      if (!requested) {
        form.setError("root", { type: "invalid_local_time" });
        return;
      }
      return controller.submit(requested, fields.reason);
    }),
  };
}
