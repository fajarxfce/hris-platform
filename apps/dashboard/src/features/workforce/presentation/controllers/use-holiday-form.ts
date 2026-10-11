import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { HolidayEditorState } from "../models/holiday-editor-state";
import { type HolidayFormValues, holidayFormValues } from "../models/holiday-form-values";
import type { HolidayEditorController } from "./holiday-editor-controller";

export function useHolidayForm(
  controller: HolidayEditorController,
  state: HolidayEditorState,
  today: string,
) {
  const values = useMemo(() => holidayFormValues(state.holiday, today), [state.holiday, today]);
  const form = useForm<HolidayFormValues>({ values });
  const workDate = useController({ name: "workDate", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const requestDeparture = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    workDate: workDate.field,
    name: name.field,
    active: active.field,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    refresh: () =>
      requestDeparture(() => {
        form.reset(values);
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        workDate: fields.workDate,
        name: fields.name,
        active: fields.active === "true",
        reason: fields.reason,
      }),
    ),
  };
}
