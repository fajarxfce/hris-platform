import { useMemo } from "react";
import { useController, useFieldArray, useForm } from "react-hook-form";
import { useNavigationProtection } from "../../../../core/presentation/navigation/use-navigation-protection";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import { isApprovalKind } from "../../domain/policies/approval-template-policy";
import type { ApprovalTemplateEditorState } from "../models/approval-template-editor-state";
import {
  type ApprovalTemplateFormValues,
  approvalTemplateFields,
  approvalTemplateFormValues,
} from "../models/approval-template-form-values";
import type { ApprovalTemplateEditorController } from "./approval-template-editor-controller";

export function useApprovalTemplateForm(
  controller: ApprovalTemplateEditorController,
  state: ApprovalTemplateEditorState,
  initialKind: ApprovalKind,
  date: string,
) {
  const values = useMemo(
    () => approvalTemplateFormValues(state.template, initialKind, date),
    [state.template, initialKind, date],
  );
  const form = useForm<ApprovalTemplateFormValues>({ values });
  const name = useController({ name: "name", control: form.control });
  const kind = useController({ name: "kind", control: form.control });
  const active = useController({ name: "active", control: form.control });
  const effectiveFrom = useController({ name: "effectiveFrom", control: form.control });
  const category = useController({ name: "category", control: form.control });
  const minimumAmount = useController({ name: "minimumAmount", control: form.control });
  const reason = useController({ name: "reason", control: form.control });
  const stages = useFieldArray({ name: "stages", control: form.control });
  const editable = state.stage === "editing";
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
    name: name.field,
    kind: kind.field,
    active: active.field,
    effectiveFrom: effectiveFrom.field,
    category: category.field,
    minimumAmount: minimumAmount.field,
    reason: reason.field,
    control: form.control,
    stages: stages.fields,
    editable,
    changeKind: (value: string) => {
      if (
        !editable ||
        !controller.creating ||
        !isApprovalKind(value) ||
        value === form.getValues("kind")
      )
        return;
      kind.field.onChange(value);
      stages.replace({
        assignment: value === "PAYROLL" ? "PERMISSION" : "MANAGER",
        accounts: [],
        permission: value === "PAYROLL" ? "payroll.review" : "",
      });
    },
    addStage: () => {
      if (editable && form.getValues("stages").length < 8)
        stages.append({
          assignment: form.getValues("kind") === "PAYROLL" ? "PERMISSION" : "MANAGER",
          accounts: [],
          permission: form.getValues("kind") === "PAYROLL" ? "payroll.review" : "",
        });
    },
    removeStage: (index: number) => {
      const count = form.getValues("stages").length;
      if (editable && count > 1 && Number.isInteger(index) && index >= 0 && index < count)
        stages.remove(index);
    },
    moveStage: (index: number, delta: -1 | 1) => {
      const count = form.getValues("stages").length;
      if (
        editable &&
        Number.isInteger(index) &&
        index >= 0 &&
        index < count &&
        index + delta >= 0 &&
        index + delta < count
      )
        stages.move(index, index + delta);
    },
    submit: form.handleSubmit((input) => controller.save(approvalTemplateFields(input))),
    refresh: () =>
      depart(() => {
        void controller.refresh();
      }),
  };
}
