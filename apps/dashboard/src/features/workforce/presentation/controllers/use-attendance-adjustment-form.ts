import { useMemo } from "react";
import { useController, useForm } from "react-hook-form";
import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { AttendanceEvidenceDecision } from "../../domain/entities/attendance-adjustment-change";
import type { AttendanceAdjustmentState } from "../models/attendance-adjustment-state";
import { attendanceCorrectionValues } from "../models/attendance-form-values";
import type { AttendanceAdjustmentController } from "./attendance-adjustment-controller";

export function useAttendanceAdjustmentForm(
  controller: AttendanceAdjustmentController,
  state: AttendanceAdjustmentState,
  timezone: string,
) {
  const values = useMemo(
    () => ({
      ...attendanceCorrectionValues(state.review?.attendance ?? null, timezone),
      decisions: Object.fromEntries(
        (state.review?.attendance.entries ?? [])
          .filter((entry) => entry.status === "PENDING")
          .map((entry) => [entry.id, "" as "" | "ACCEPT" | "REJECT"]),
      ),
    }),
    [state.review, timezone],
  );
  const form = useForm({ values });
  const absence = useController({ control: form.control, name: "absence" });
  const clockIn = useController({ control: form.control, name: "clockIn" });
  const clockOut = useController({ control: form.control, name: "clockOut" });
  const breakMinutes = useController({ control: form.control, name: "breakMinutes" });
  const reason = useController({
    control: form.control,
    name: "reason",
    rules: { validate: (value) => value.trim().length > 0, maxLength: 1000 },
  });
  const decisions = useController({ control: form.control, name: "decisions" });
  const editable =
    state.stage === "reviewing" &&
    state.review?.canCorrect === true &&
    (state.review.attendance.pendingCount === 0 || state.review.canReviewEvidence);
  const departure = useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    absence: absence.field,
    clockIn: clockIn.field,
    clockOut: clockOut.field,
    breakMinutes: breakMinutes.field,
    reason: reason.field,
    decisions: decisions.field.value,
    decide: (id: string, value: string) => {
      if (editable && id in form.getValues("decisions") && ["", "ACCEPT", "REJECT"].includes(value))
        decisions.field.onChange({ ...form.getValues("decisions"), [id]: value });
    },
    editable,
    invalid: Object.keys(form.formState.errors).length > 0,
    reload: () =>
      departure(() => {
        form.reset(values);
        void controller.refresh();
      }),
    submit: form.handleSubmit((fields) => {
      if (!editable || !state.review) return;
      const chosen: AttendanceEvidenceDecision[] = [];
      for (const entry of state.review.attendance.entries.filter(
        (item) => item.status === "PENDING",
      )) {
        const decision = fields.decisions[entry.id];
        if (decision !== "ACCEPT" && decision !== "REJECT") {
          form.setError("decisions", { type: "required" });
          return;
        }
        chosen.push({ eventId: entry.id, expectedVersion: entry.version, decision });
      }
      const start = fields.absence
        ? null
        : parseCompanyDateTime(
            fields.clockIn,
            timezone,
            state.review.attendance.correction?.clockIn,
          );
      const end = fields.absence
        ? null
        : parseCompanyDateTime(
            fields.clockOut,
            timezone,
            state.review.attendance.correction?.clockOut,
          );
      if (
        !fields.absence &&
        (start === null || end === null || !/^\d{1,4}$/u.test(fields.breakMinutes))
      ) {
        form.setError("root", { type: "invalid_attendance_local_time" });
        return;
      }
      return controller.save({
        clockIn: start,
        clockOut: end,
        breakMinutes: fields.absence ? 0 : Number(fields.breakMinutes),
        reason: fields.reason,
        decisions: chosen,
      });
    }),
  };
}
