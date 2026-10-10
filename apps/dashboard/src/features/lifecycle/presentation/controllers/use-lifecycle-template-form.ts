import { useCallback, useEffect, useMemo } from "react";
import { useController, useFieldArray, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { LifecycleTemplateEditorState } from "../models/lifecycle-template-editor-state";
import {
  type LifecycleTemplateFormValues,
  lifecycleTemplateFields,
  lifecycleTemplateFormValues,
} from "../models/lifecycle-template-form-values";
import type { LifecycleTemplateEditorController } from "./lifecycle-template-editor-controller";

export function useLifecycleTemplateForm(
  controller: LifecycleTemplateEditorController,
  state: LifecycleTemplateEditorState,
) {
  const values = useMemo(() => lifecycleTemplateFormValues(state.template), [state.template]);
  const form = useForm<LifecycleTemplateFormValues>({ defaultValues: values });
  const reset = form.reset;
  useEffect(() => reset(values), [reset, values]);
  const code = useController({ name: "code", control: form.control });
  const name = useController({ name: "name", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const tasks = useFieldArray({ name: "tasks", control: form.control });
  const editable = state.stage === "editing";
  const remove = tasks.remove;
  const getValues = form.getValues;
  const count = tasks.fields.length;
  const removeTask = useCallback(
    (index: number) => {
      const currentCount = getValues("tasks").length;
      if (
        editable &&
        currentCount > 1 &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < currentCount
      )
        remove(index);
    },
    [editable, getValues, remove],
  );
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
    code: code.field,
    name: name.field,
    kind: kind.field,
    active: active.field,
    reason: reason.field,
    control: form.control,
    tasks: tasks.fields,
    editable,
    removeTask,
    canAdd: editable && count < 64,
    canRemove: editable && count > 1,
    addTask: () => {
      if (editable && getValues("tasks").length < 64)
        tasks.append({ key: "", title: "", required: true, dueDays: "0" });
    },
    submit: form.handleSubmit((fields) => controller.save(lifecycleTemplateFields(fields))),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
