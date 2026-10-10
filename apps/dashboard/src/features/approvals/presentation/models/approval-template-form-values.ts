import type { ApprovalAssignee } from "../../domain/entities/approval-assignee";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import type { ApprovalAssignment, ApprovalTemplate } from "../../domain/entities/approval-template";
import type { ApprovalTemplateFields } from "../controllers/approval-template-editor-controller";

export type ApprovalStageFormValue = {
  assignment: ApprovalAssignment;
  accounts: ApprovalAssignee[];
  permission: string;
};
export type ApprovalTemplateFormValues = {
  name: string;
  kind: ApprovalKind;
  active: boolean;
  effectiveFrom: string;
  category: string;
  minimumAmount: string;
  stages: ApprovalStageFormValue[];
  reason: string;
};
export function approvalTemplateFormValues(
  template: ApprovalTemplate | null,
  kind: ApprovalKind,
  date: string,
): ApprovalTemplateFormValues {
  return {
    name: template?.name ?? "",
    kind: template?.kind ?? kind,
    active: template?.active ?? true,
    effectiveFrom: template?.effectiveFrom ?? date,
    category: template?.category ?? "",
    minimumAmount: template?.minimumAmount ?? "0",
    reason: "",
    stages: template?.stages.map((stage) => ({
      assignment: stage.assignment,
      accounts: stage.accountIds.map((id) => ({ id, displayName: id })),
      permission: stage.permission ?? "",
    })) ?? [
      {
        assignment: kind === "PAYROLL" ? "PERMISSION" : "MANAGER",
        accounts: [],
        permission: kind === "PAYROLL" ? "payroll.review" : "",
      },
    ],
  };
}
export function approvalTemplateFields(values: ApprovalTemplateFormValues): ApprovalTemplateFields {
  return {
    name: values.name,
    kind: values.kind,
    active: values.active,
    effectiveFrom: values.effectiveFrom,
    category: values.category || null,
    minimumAmount: values.minimumAmount,
    reason: values.reason,
    stages: values.stages.map((stage) => ({
      assignment: stage.assignment,
      accountIds: stage.accounts.map((account) => account.id),
      permission: stage.permission || null,
    })),
  };
}
