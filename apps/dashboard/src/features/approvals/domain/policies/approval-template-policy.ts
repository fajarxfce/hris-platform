import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { ApprovalKind } from "../entities/approval-request";
import { approvalKinds } from "../entities/approval-request";
import type { ApprovalStageRule, ApprovalTemplateSearch } from "../entities/approval-template";
import type { ApprovalTemplateChange } from "../entities/approval-template-change";

export const approvalActionPermissions: Readonly<Record<ApprovalKind, readonly string[]>> = {
  LEAVE: ["leave.approve", "leave.team.approve"],
  LEAVE_CANCELLATION: ["leave.approve", "leave.team.approve"],
  EXPENSE: ["expenses.approve", "expenses.team.approve"],
  OVERTIME: ["overtime.approve", "overtime.team.approve"],
  PAYROLL: ["payroll.review"],
};
export const isApprovalKind = (kind: string): kind is ApprovalKind =>
  approvalKinds.some((value) => value === kind);
export const isApprovalDate = (value: string): boolean =>
  isCalendarDate(value) && value >= "1900-01-01" && value <= "2200-12-31";
export const validApprovalTemplateSearch = (search: ApprovalTemplateSearch): boolean =>
  isApprovalKind(search.kind) &&
  isApprovalDate(search.asOf) &&
  (search.after === null || isUuid(search.after));
export const validApprovalRevision = (revision: string | null): boolean =>
  revision === null ||
  (/^(0|[1-9]\d{0,15})$/u.test(revision) && Number.isSafeInteger(Number(revision)));
export const validApprovalMinimumAmount = (value: string): boolean =>
  /^(0|[1-9]\d{0,17})(?:\.\d{1,2})?$/u.test(value) &&
  value
    .replace(/(\.\d*?)0+$/u, "$1")
    .replace(".", "")
    .replace(/^0+/u, "").length <= 18;

export function validApprovalStage(rule: ApprovalStageRule, kind: ApprovalKind): boolean {
  switch (rule.assignment) {
    case "MANAGER":
      return kind !== "PAYROLL" && rule.accountIds.length === 0 && rule.permission === null;
    case "NAMED":
      return (
        rule.permission === null &&
        rule.accountIds.length >= 1 &&
        rule.accountIds.length <= 25 &&
        rule.accountIds.every(isUuid) &&
        new Set(rule.accountIds.map((id) => id.toLowerCase())).size === rule.accountIds.length
      );
    case "PERMISSION":
      return (
        rule.accountIds.length === 0 &&
        rule.permission !== null &&
        approvalActionPermissions[kind].includes(rule.permission)
      );
  }
}
export function normalizeApprovalTemplate(change: ApprovalTemplateChange): ApprovalTemplateChange {
  return Object.freeze({
    ...change,
    id: change.id.toLowerCase(),
    name: change.name.trim(),
    category: change.category?.trim() || null,
    minimumAmount: change.minimumAmount.trim(),
    reason: change.reason.trim(),
    stages: Object.freeze(
      change.stages.map((stage) =>
        Object.freeze({
          ...stage,
          accountIds: Object.freeze(stage.accountIds.map((id) => id.toLowerCase() as typeof id)),
        }),
      ),
    ),
  });
}
export function validApprovalTemplateChange(change: ApprovalTemplateChange): boolean {
  return (
    isUuid(change.id) &&
    isApprovalKind(change.kind) &&
    change.name.length >= 1 &&
    change.name.length <= 200 &&
    (change.expectedVersion === null ||
      (Number.isSafeInteger(change.expectedVersion) &&
        change.expectedVersion >= 0 &&
        change.expectedVersion < Number.MAX_SAFE_INTEGER)) &&
    isApprovalDate(change.effectiveFrom) &&
    (change.category?.length ?? 0) <= 80 &&
    validApprovalMinimumAmount(change.minimumAmount) &&
    change.reason.length >= 1 &&
    change.reason.length <= 1000 &&
    change.stages.length >= 1 &&
    change.stages.length <= 8 &&
    change.stages.every((stage) => validApprovalStage(stage, change.kind))
  );
}

/** A rejected retry cannot resolve an earlier command whose response was lost. */
export const approvalTemplateSaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_approval_template",
    "invalid_decimal_amount",
    "invalid_approval_stage",
    "payroll_approval_assignment_invalid",
    "approval_kind_immutable",
    "approval_template_not_found",
    "approval_template_limit",
    "approval_policy_capacity",
    "approver_unavailable",
    "stale_version",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
