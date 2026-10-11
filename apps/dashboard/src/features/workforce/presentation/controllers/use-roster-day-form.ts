import { type ChangeEvent, useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";
import type { CalendarEditorState } from "../models/calendar-editor-state";
import { type RosterDayFormValues, rosterDayFormValues } from "../models/calendar-form-values";
import { calendarShiftOptions, shiftChoiceKey } from "../models/calendar-shift-options";
import type { RosterDayController } from "./roster-day-controller";

export function useRosterDayForm(
  controller: RosterDayController,
  state: CalendarEditorState,
  shifts: readonly ShiftDefinition[],
  locale: Locale,
  reloadShifts: () => void,
) {
  const values = useMemo(() => rosterDayFormValues(state.review), [state.review]);
  const form = useForm<RosterDayFormValues>({ values });
  const shift = useController({ name: "shift", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const options = useMemo(
    () => calendarShiftOptions(shifts, [shift.field.value], locale),
    [shifts, shift.field.value, locale],
  );
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
    options,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    shift: {
      value: shiftChoiceKey(shift.field.value),
      onBlur: shift.field.onBlur,
      onChange: (event: ChangeEvent<HTMLSelectElement>) => {
        const selected = options.find((option) => option.key === event.target.value);
        if (event.target.value !== "" && !selected) return;
        shift.field.onChange(selected?.snapshot ?? null);
      },
    },
    refresh: () =>
      requestDeparture(() => {
        form.reset(values);
        void controller.refresh();
        reloadShifts();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({
        shift: fields.shift ? { id: fields.shift.id, version: fields.shift.revision } : null,
        reason: fields.reason,
      }),
    ),
  };
}
