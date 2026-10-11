import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AttendanceEditorState } from "../models/attendance-editor-state";
import {
  type AttendanceCorrectionValues,
  attendanceCorrectionValues,
} from "../models/attendance-form-values";
import type { AttendanceEditorController } from "./attendance-editor-controller";

export function useAttendanceCorrectionForm(
  controller: AttendanceEditorController,
  state: AttendanceEditorState,
  timezone: string,
  locale: Locale,
) {
  const values = useMemo(
    () => attendanceCorrectionValues(state.day, timezone),
    [state.day, timezone],
  );
  const form = useForm<AttendanceCorrectionValues>({ values });
  const absence = useController({ name: "absence", control: form.control });
  const clockIn = useController({ name: "clockIn", control: form.control });
  const clockOut = useController({ name: "clockOut", control: form.control });
  const breakMinutes = useController({ name: "breakMinutes", control: form.control });
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
    absence: {
      checked: absence.field.value,
      onChange: (checked: boolean) => absence.field.onChange(checked),
    },
    clockIn: clockIn.field,
    clockOut: clockOut.field,
    breakMinutes: breakMinutes.field,
    reason: reason.field,
    editable: state.stage === "editing",
    dirty: form.formState.isDirty,
    error: form.formState.errors.root
      ? failureMessage(
          { code: "invalid_attendance_local_time", fields: {}, parameters: {} },
          locale,
        )
      : null,
    refresh: () =>
      requestDeparture(() => {
        form.reset(values);
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) => {
      const start = fields.absence
        ? null
        : parseCompanyDateTime(fields.clockIn, timezone, state.day?.correction?.clockIn);
      const end = fields.absence
        ? null
        : parseCompanyDateTime(fields.clockOut, timezone, state.day?.correction?.clockOut);
      if (
        !fields.absence &&
        (start === null || end === null || !/^\d{1,4}$/u.test(fields.breakMinutes))
      ) {
        form.setError("root", { type: "invalid_attendance_local_time" });
        return;
      }
      return controller.correct({
        clockIn: start,
        clockOut: end,
        breakMinutes: fields.absence ? 0 : Number(fields.breakMinutes),
        reason: fields.reason,
      });
    }),
  };
}
