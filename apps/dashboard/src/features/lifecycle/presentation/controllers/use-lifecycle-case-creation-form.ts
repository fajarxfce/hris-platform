import { useReducer } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { LifecycleCaseCreationState } from "../models/lifecycle-case-creation-state";
import type {
  LifecycleCaseCreationController,
  LifecycleCaseCreationFields,
} from "./lifecycle-case-creation-controller";

export function useLifecycleCaseCreationForm(
  controller: LifecycleCaseCreationController,
  state: LifecycleCaseCreationState,
  initialDate: string,
) {
  const form = useForm<LifecycleCaseCreationFields>({
    defaultValues: { template: null, targetDate: initialDate, reason: "" },
  });
  const targetDate = useController({ name: "targetDate", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const template = useWatch({ name: "template", control: form.control });
  const [picker, openPicker] = useReducer((_old: boolean, next: boolean) => next, false);
  const editable = state.stage === "editing";
  useNavigationProtection(
    state.stage === "saving"
      ? "pending"
      : state.stage === "unconfirmed"
        ? "unconfirmed"
        : state.stage !== "saved" && form.formState.isDirty
          ? "dirty"
          : "none",
  );
  return {
    targetDate: targetDate.field,
    reason: reason.field,
    template,
    editable,
    picker,
    choose: () => {
      if (editable) openPicker(true);
    },
    closePicker: () => openPicker(false),
    select: (selected: LifecycleTemplate) => {
      if (editable) form.setValue("template", selected, { shouldDirty: true });
      openPicker(false);
    },
    submit: form.handleSubmit((fields) => controller.save(fields)),
  };
}
