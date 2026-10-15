import { useEffect, useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LeavePolicyDefinition } from "../../domain/entities/leave-policy-definition";
import type { LeaveAccrualScheduleStartState } from "../models/leave-accrual-schedule-state";
import type { LeaveAccrualScheduleStartController } from "./leave-accrual-schedule-start-controller";

type Values = {
  type: LeavePolicyDefinition | null;
  firstPeriod: string;
  periods: string;
  reason: string;
};
export function useLeaveAccrualScheduleForm(
  controller: LeaveAccrualScheduleStartController,
  state: LeaveAccrualScheduleStartState,
  companyId: CompanyId,
  today: string,
) {
  const form = useForm<Values>({
    defaultValues: { type: null, firstPeriod: today.slice(0, 7), periods: "3", reason: "" },
  });
  const firstPeriod = useController({ name: "firstPeriod", control: form.control }),
    periods = useController({ name: "periods", control: form.control }),
    reason = useController({ name: "reason", control: form.control }),
    type = useWatch({ name: "type", control: form.control });
  const [pickerOpen, setPickerOpen] = useReducer((_old: boolean, next: boolean) => next, false);
  const { reset, setValue, getValues } = form;
  useEffect(() => {
    if (
      state.stage === "saved" ||
      state.stage === "unavailable" ||
      (state.stage === "unconfirmed" && state.review === null)
    ) {
      reset({ type: null, firstPeriod: "", periods: "", reason: "" });
      setPickerOpen(false);
    }
  }, [state.stage, state.review, reset]);
  useEffect(() => {
    if (state.stage === "reviewing" && state.review && !getValues("type")) {
      reset({
        type: state.review.periods[0]?.type ?? null,
        firstPeriod: state.review.selection.firstPeriod,
        periods: String(state.review.selection.periods),
        reason: getValues("reason"),
      });
    }
  }, [state.stage, state.review, getValues, reset]);
  const depart = useNavigationProtection(
    state.stage === "saving" || state.stage === "preparing"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    firstPeriod: firstPeriod.field,
    periods: periods.field,
    reason: reason.field,
    type,
    pickerOpen,
    chooseType: () => {
      if (state.stage === "editing") setPickerOpen(true);
    },
    closePicker: () => setPickerOpen(false),
    selectType: (value: LeavePolicyDefinition) => {
      if (state.stage === "editing" && value.companyId === companyId)
        setValue("type", value, { shouldDirty: true });
      setPickerOpen(false);
    },
    prepare: form.handleSubmit((fields) =>
      controller.prepare({
        typeId: fields.type?.id ?? "",
        firstPeriod: fields.firstPeriod,
        periods: Number(fields.periods),
      }),
    ),
    submit: form.handleSubmit((fields) => controller.save(fields.reason)),
    revise: controller.revise,
    reload: () =>
      depart(() => {
        reset({ type: null, firstPeriod: today.slice(0, 7), periods: "3", reason: "" });
        setPickerOpen(false);
        controller.refresh();
      }),
  };
}
