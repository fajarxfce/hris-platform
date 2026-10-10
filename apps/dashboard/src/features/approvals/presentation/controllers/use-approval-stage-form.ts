import { useEffect, useReducer } from "react";
import { type Control, useController } from "react-hook-form";
import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import { approvalAssignments } from "../../domain/entities/approval-template";
import { approvalActionPermissions } from "../../domain/policies/approval-template-policy";
import type { ApprovalTemplateFormValues } from "../models/approval-template-form-values";

export function useApprovalStageForm(
  control: Control<ApprovalTemplateFormValues>,
  index: number,
  kind: ApprovalKind,
  editable: boolean,
) {
  const assignment = useController({ name: `stages.${index}.assignment`, control });
  const accounts = useController({ name: `stages.${index}.accounts`, control });
  const permission = useController({ name: `stages.${index}.permission`, control });
  const [pickerKind, setPickerKind] = useReducer(
    (_: ApprovalKind | null, next: ApprovalKind | null) => next,
    null,
  );
  useEffect(() => {
    if (pickerKind !== null && (!editable || pickerKind !== kind)) setPickerKind(null);
  }, [pickerKind, kind, editable]);
  return {
    assignment: assignment.field,
    accounts: accounts.field.value,
    permission: permission.field,
    picker: editable && pickerKind === kind && assignment.field.value === "NAMED",
    changeAssignment: (value: string) => {
      if (!editable || !approvalAssignments.some((item) => item === value)) return;
      assignment.field.onChange(value);
      accounts.field.onChange([]);
      permission.field.onChange(
        value === "PERMISSION" ? (approvalActionPermissions[kind][0] ?? "") : "",
      );
      setPickerKind(null);
    },
    openPicker: () => {
      if (editable && assignment.field.value === "NAMED" && accounts.field.value.length < 25)
        setPickerKind(kind);
    },
    closePicker: () => setPickerKind(null),
    select: (account: ApprovalAssignee) => {
      if (
        editable &&
        assignment.field.value === "NAMED" &&
        accounts.field.value.length < 25 &&
        !accounts.field.value.some((item) => item.id === account.id)
      )
        accounts.field.onChange([...accounts.field.value, account]);
      setPickerKind(null);
    },
    remove: (id: string) => {
      if (editable) accounts.field.onChange(accounts.field.value.filter((item) => item.id !== id));
    },
  };
}
