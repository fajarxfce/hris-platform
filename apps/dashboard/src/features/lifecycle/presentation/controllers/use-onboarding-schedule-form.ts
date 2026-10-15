import { useEffect, useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleAssignee } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { OnboardingScheduleStartState } from "../models/onboarding-schedule-state";
import type { OnboardingScheduleStartController } from "./onboarding-schedule-start-controller";

type Values = {
  template: LifecycleTemplate | null;
  startsOn: string;
  reason: string;
  assignees: Record<string, LifecycleAssignee>;
};
type Picker = Readonly<{ kind: "template" }> | Readonly<{ kind: "assignee"; key: string }> | null;
const empty = (): Values => ({ template: null, startsOn: "", reason: "", assignees: {} });
export function useOnboardingScheduleForm(
  controller: OnboardingScheduleStartController,
  state: OnboardingScheduleStartState,
) {
  const form = useForm<Values>({ defaultValues: empty() });
  const startsOn = useController({ name: "startsOn", control: form.control }),
    reason = useController({ name: "reason", control: form.control });
  const template = useWatch({ name: "template", control: form.control }),
    assignees = useWatch({ name: "assignees", control: form.control });
  const [picker, openPicker] = useReducer((_old: Picker, next: Picker) => next, null);
  const { reset, getValues, setValue } = form;
  useEffect(() => {
    if (state.employee === null) {
      reset(empty());
      openPicker(null);
    } else if (!getValues("startsOn")) setValue("startsOn", state.employee.terms.startDate);
  }, [state.employee, reset, getValues, setValue]);
  useEffect(() => {
    if (state.review === null) {
      setValue("assignees", {});
      openPicker(null);
    }
  }, [state.review, setValue]);
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
    startsOn: startsOn.field,
    reason: reason.field,
    template,
    assignees,
    picker,
    chooseTemplate: () => {
      if (state.stage === "editing") openPicker({ kind: "template" });
    },
    selectTemplate: (value: LifecycleTemplate) => {
      if (state.stage === "editing" && value.companyId === state.employee?.companyId)
        setValue("template", value, { shouldDirty: true });
      openPicker(null);
    },
    chooseAssignee: (key: string) => {
      if (state.stage === "reviewing") openPicker({ kind: "assignee", key });
    },
    selectAssignee: (value: LifecycleAssignee) => {
      if (
        state.stage === "reviewing" &&
        picker?.kind === "assignee" &&
        value.companyId === state.review?.companyId
      )
        setValue(
          "assignees",
          { ...getValues("assignees"), [picker.key]: value },
          { shouldDirty: true },
        );
      openPicker(null);
    },
    clearAssignee: () => {
      if (state.stage === "reviewing" && picker?.kind === "assignee") {
        const next = { ...getValues("assignees") };
        delete next[picker.key];
        setValue("assignees", next, { shouldDirty: true });
      }
      openPicker(null);
    },
    closePicker: () => openPicker(null),
    prepare: form.handleSubmit((fields) => controller.prepare(fields.template, fields.startsOn)),
    submit: form.handleSubmit((fields) =>
      controller.save({ reason: fields.reason, assignees: fields.assignees }),
    ),
    revise: controller.revise,
    reload: () =>
      depart(() => {
        reset(empty());
        openPicker(null);
        void controller.refresh();
      }),
  };
}
