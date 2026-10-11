import { type ChangeEvent, useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { weekdays } from "../../domain/entities/schedule-assignment";
import type { ShiftDefinition } from "../../domain/entities/shift-definition";
import { calendarMessages } from "../i18n/calendar-messages";
import type { CalendarEditorState } from "../models/calendar-editor-state";
import {
  type WeeklyScheduleFormValues,
  weeklyScheduleFormValues,
  weeklyScheduleReferences,
} from "../models/calendar-form-values";
import { calendarShiftOptions, shiftChoiceKey } from "../models/calendar-shift-options";
import type { WeeklyScheduleController } from "./weekly-schedule-controller";

export function useWeeklyScheduleForm(
  controller: WeeklyScheduleController,
  state: CalendarEditorState,
  shifts: readonly ShiftDefinition[],
  locale: Locale,
  reloadShifts: () => void,
) {
  const values = useMemo(() => weeklyScheduleFormValues(state.review), [state.review]);
  const form = useForm<WeeklyScheduleFormValues>({ values });
  const days = useController({ name: "days", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const options = useMemo(
    () => calendarShiftOptions(shifts, Object.values(days.field.value), locale),
    [shifts, days.field.value, locale],
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
  const text = calendarMessages(locale);
  return {
    options,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    days: weekdays.map((day) => ({
      day,
      label: text[day],
      value: shiftChoiceKey(days.field.value[day]),
      onBlur: days.field.onBlur,
      onChange: (event: ChangeEvent<HTMLSelectElement>) => {
        const selected = options.find((option) => option.key === event.target.value);
        if (event.target.value !== "" && !selected) return;
        days.field.onChange({ ...days.field.value, [day]: selected?.snapshot ?? null });
      },
    })),
    refresh: () =>
      requestDeparture(() => {
        form.reset(values);
        void controller.refresh();
        reloadShifts();
      }),
    submit: form.handleSubmit((fields) =>
      controller.save({ days: weeklyScheduleReferences(fields.days), reason: fields.reason }),
    ),
  };
}
