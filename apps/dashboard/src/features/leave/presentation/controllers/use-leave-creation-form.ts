import { useReducer } from "react";
import { useController, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeavePreparationEmployee } from "../../domain/entities/leave-preparation-employee";
import type { LeaveRequestType } from "../../domain/entities/leave-request-type";
import { isLeaveRequestDate } from "../../domain/policies/leave-preparation-policy";
import { type LeaveCreationState, leaveCreationIsLocked } from "../models/leave-creation-state";
import { type LeaveCreationValues, selectedLeaveDays } from "../models/leave-creation-values";
import type { LeaveCreationController } from "./leave-creation-controller";

type Picker = "employee" | "type" | "evidence" | null;
export function useLeaveCreationForm(
  controller: LeaveCreationController,
  state: LeaveCreationState,
  today: string,
  canCreate: boolean,
) {
  const form = useForm<LeaveCreationValues>({
    defaultValues: {
      from: today,
      until: today,
      firstPortion: "FULL",
      lastPortion: "FULL",
      reason: "",
    },
  });
  const from = useController({ name: "from", control: form.control });
  const until = useController({ name: "until", control: form.control });
  const first = useController({ name: "firstPortion", control: form.control });
  const last = useController({ name: "lastPortion", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const [picker, openPicker] = useReducer((_old: Picker, next: Picker) => next, null);
  const locked = leaveCreationIsLocked(state),
    editable = canCreate && !locked;
  useNavigationProtection(
    state.stage === "submitting"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && (form.formState.isDirty || state.employee !== null)
          ? "dirty"
          : "none",
  );
  const selectionChanged = (
    field: "from" | "until" | "firstPortion" | "lastPortion",
    value: string,
  ) => {
    if (!editable) return;
    form.setValue(field, value, { shouldDirty: true });
    form.clearErrors("root");
    controller.changeDates(form.getValues("from"));
  };
  const submit = form.handleSubmit(async (values) => {
    if (state.stage === "reviewing") return controller.confirm(values.reason);
    const days = selectedLeaveDays(values);
    if (!days.ok) {
      form.setError("root", { type: "invalid_range" });
      return;
    }
    return controller.preview(days.value);
  });
  return {
    submit,
    picker,
    editable,
    showForm:
      state.stage !== "saved" &&
      state.stage !== "unavailable" &&
      !(state.stage === "unconfirmed" && state.employee === null),
    canChooseEmployee: editable,
    canChooseType: editable && state.employee !== null && isLeaveRequestDate(from.field.value),
    canChooseEvidence: editable && state.employee !== null && state.evidence.length < 3,
    canPreview:
      editable && state.stage !== "previewing" && state.employee !== null && state.type !== null,
    canSubmit: state.stage === "reviewing",
    differentDates: from.field.value !== until.field.value,
    invalidRange: Boolean(form.formState.errors.root),
    from: {
      ...from.field,
      onChange: (_event: unknown, data: { value: string }) => selectionChanged("from", data.value),
    },
    until: {
      ...until.field,
      onChange: (_event: unknown, data: { value: string }) => selectionChanged("until", data.value),
    },
    first: {
      ...first.field,
      onChange: (_event: unknown, data: { value: string }) =>
        selectionChanged("firstPortion", data.value),
    },
    last: {
      ...last.field,
      onChange: (_event: unknown, data: { value: string }) =>
        selectionChanged("lastPortion", data.value),
    },
    reason: reason.field,
    openEmployees: () => {
      if (editable) openPicker("employee");
    },
    openTypes: () => {
      if (editable && state.employee) openPicker("type");
    },
    openEvidence: () => {
      if (editable && state.employee) openPicker("evidence");
    },
    closePicker: () => openPicker(null),
    selectEmployee: (employee: LeavePreparationEmployee) => {
      if (!editable) return;
      form.setValue("reason", "");
      openPicker(null);
      controller.selectEmployee(employee);
    },
    selectType: (type: LeaveRequestType) => {
      if (!editable) return;
      if (!type.allowPartialDays) {
        form.setValue("firstPortion", "FULL");
        form.setValue("lastPortion", "FULL");
      }
      openPicker(null);
      controller.selectType(type, from.field.value);
    },
  };
}
