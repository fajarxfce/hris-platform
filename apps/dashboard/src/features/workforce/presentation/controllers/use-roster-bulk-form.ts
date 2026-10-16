import { useEffect, useMemo, useRef } from "react";
import { useController, useForm } from "react-hook-form";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import { type Weekday, weekdays } from "../../domain/entities/schedule-assignment";
import type { ShiftSnapshot } from "../../domain/entities/shift-snapshot";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";
import {
  initialRosterBulkValues,
  type RosterBulkFormValues,
  rosterBulkDates,
} from "../models/roster-bulk-form-values";
import type { RosterBulkState } from "../models/roster-bulk-state";
import type { RosterBulkController } from "./roster-bulk-controller";

export function useRosterBulkForm(
  controller: RosterBulkController,
  state: RosterBulkState,
  company: CompanyId,
  today: string,
) {
  const initial = useMemo(() => initialRosterBulkValues(today), [today]);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const form = useForm<RosterBulkFormValues>({ defaultValues: initial });
  const from = useController({
    control: form.control,
    name: "from",
    rules: {
      validate: (value) => rosterBulkDates(value, form.getValues("until"), weekdays) !== null,
    },
  });
  const until = useController({
    control: form.control,
    name: "until",
    rules: {
      validate: (value) => rosterBulkDates(form.getValues("from"), value, weekdays) !== null,
    },
  });
  const days = useController({
    control: form.control,
    name: "weekdays",
    rules: {
      validate: (value) =>
        (rosterBulkDates(form.getValues("from"), form.getValues("until"), value)?.length ?? 0) > 0,
    },
  });
  const mode = useController({ control: form.control, name: "mode" });
  const shift = useController({
    control: form.control,
    name: "shift",
    rules: { validate: (value) => form.getValues("mode") !== "SHIFT" || value !== null },
  });
  const employees = useController({
    control: form.control,
    name: "employees",
    rules: { validate: (value) => value.length > 0 && value.length <= 50 },
  });
  const reason = useController({
    control: form.control,
    name: "reason",
    rules: {
      maxLength: 1000,
      validate: (value) => state.stage !== "reviewing" || value.trim().length > 0,
    },
  });
  const { reset, clearErrors, setError, setValue } = form;
  useEffect(() => {
    if (
      state.stage === "unavailable" ||
      state.stage === "saved" ||
      (state.stage === "unconfirmed" && state.review === null)
    )
      reset(initial);
  }, [state.stage, state.review, initial, reset]);
  useEffect(() => {
    if (state.stage === "reviewing") headingRef.current?.focus({ preventScroll: true });
  }, [state.stage]);
  const departure = useNavigationProtection(
    state.stage === "preparing" || state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  const dates = useMemo(
    () => rosterBulkDates(from.field.value, until.field.value, days.field.value),
    [from.field.value, until.field.value, days.field.value],
  );
  const updateEmployees = (values: readonly WorkforceEmployee[], selected: boolean) => {
    if (state.stage !== "editing" || values.some((employee) => employee.companyId !== company))
      return;
    const next = new Map(form.getValues("employees").map((employee) => [employee.id, employee]));
    for (const employee of values) {
      if (selected) next.set(employee.id, employee);
      else next.delete(employee.id);
    }
    if (next.size > 50) {
      setError("employees", { type: "capacity" });
      return;
    }
    clearErrors("employees");
    setValue("employees", [...next.values()], { shouldDirty: true });
  };
  return {
    headingRef,
    from: from.field,
    until: until.field,
    mode: mode.field,
    reason: reason.field,
    weekdays: days.field.value,
    changeWeekdays: (values: string[]) => {
      if (state.stage === "editing" && values.every((value) => weekdays.includes(value as Weekday)))
        days.field.onChange(values);
    },
    shift: shift.field.value,
    selectShift: (value: ShiftSnapshot | null) => {
      if (state.stage === "editing") shift.field.onChange(value);
    },
    employees: employees.field.value,
    toggleEmployee: (employee: WorkforceEmployee, selected: boolean) =>
      updateEmployees([employee], selected),
    togglePage: updateEmployees,
    removeEmployee: (employee: WorkforceEmployee) => updateEmployees([employee], false),
    clearEmployees: () => updateEmployees(form.getValues("employees"), false),
    dates,
    cellCount: (dates?.length ?? 0) * employees.field.value.length,
    lookupDate:
      isCalendarDate(until.field.value) && until.field.value >= "1900-01-01"
        ? until.field.value
        : null,
    invalidRange: !!from.fieldState.error || !!until.fieldState.error,
    invalidDays: !!days.fieldState.error,
    invalidShift: !!shift.fieldState.error,
    invalidEmployees: !!employees.fieldState.error,
    employeeLimit: employees.fieldState.error?.type === "capacity",
    invalidReason: !!reason.fieldState.error,
    prepare: form.handleSubmit((fields) =>
      controller.prepare({
        employeeIds: fields.employees.map((employee) => employee.id),
        dates: rosterBulkDates(fields.from, fields.until, fields.weekdays) ?? [],
        mode: fields.mode,
        shift:
          fields.mode === "SHIFT" && fields.shift
            ? { id: fields.shift.id, version: fields.shift.revision }
            : null,
      }),
    ),
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    revise: controller.revise,
    reload: () =>
      departure(() => {
        reset(initial);
        controller.refresh();
      }),
  };
}
