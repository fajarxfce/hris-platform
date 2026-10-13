import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leavePortions } from "../entities/leave-day";
import type { LeaveEvidenceOption } from "../entities/leave-evidence-option";
import type { ReviewedLeaveSelection } from "../entities/leave-request-preview";
import type { LeaveRequestSelection, RequestedLeaveDay } from "../entities/leave-request-selection";
import type { LeaveRequestSubmission } from "../entities/leave-request-submission";
import { leaveActionWasRejected } from "./leave-request-action-policy";

export const canPrepareLeave = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.manage");
export const isLeaveRequestDate = (date: string): boolean =>
  isCalendarDate(date) && date >= "1900-01-01" && date <= "2200-12-31";

export function requestedLeaveRange(
  from: string,
  until: string,
  portion: string,
): Result<readonly RequestedLeaveDay[]> {
  if (
    !isLeaveRequestDate(from) ||
    !isLeaveRequestDate(until) ||
    until < from ||
    !leavePortions.some((value) => value === portion)
  )
    return failed("invalid_leave_selection");
  const first = Date.parse(`${from}T00:00:00Z`),
    last = Date.parse(`${until}T00:00:00Z`);
  const count = (last - first) / 86_400_000 + 1;
  if (!Number.isInteger(count) || count < 1 || count > 366)
    return failed("invalid_leave_selection");
  return success(
    Object.freeze(
      Array.from({ length: count }, (_, index) =>
        Object.freeze({
          workDate: new Date(first + index * 86_400_000).toISOString().slice(0, 10),
          portion: portion as RequestedLeaveDay["portion"],
        }),
      ),
    ),
  );
}

export function validateLeaveSelection(
  selection: LeaveRequestSelection,
): Result<LeaveRequestSelection> {
  if (
    !isUuid(selection.employeeId) ||
    !isUuid(selection.typeId) ||
    selection.days.length < 1 ||
    selection.days.length > 366 ||
    new Set(selection.days.map((day) => day.workDate)).size !== selection.days.length ||
    selection.days.some(
      (day) => !isLeaveRequestDate(day.workDate) || !leavePortions.includes(day.portion),
    )
  )
    return failed("invalid_leave_selection");
  const days = [...selection.days].sort((left, right) =>
    left.workDate.localeCompare(right.workDate),
  );
  const first = days[0],
    last = days.at(-1);
  if (
    !first ||
    !last ||
    Date.parse(`${last.workDate}T00:00:00Z`) - Date.parse(`${first.workDate}T00:00:00Z`) >
      365 * 86_400_000
  )
    return failed("invalid_leave_selection");
  return success(
    Object.freeze({
      employeeId: selection.employeeId.toLowerCase(),
      typeId: selection.typeId.toLowerCase(),
      days: Object.freeze(days.map((day) => Object.freeze({ ...day }))),
    }),
  );
}

export function validateLeaveSubmission(
  operation: OperationId,
  input: LeaveRequestSubmission,
): Result<LeaveRequestSubmission> {
  const selected = validateLeaveSelection(input);
  if (!selected.ok) return selected;
  const reason = input.reason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(input.companyId) ||
    !isUuid(input.id) ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_leave_submission");
  if (!/^[0-9a-f]{64}$/u.test(input.previewRevision)) return failed("invalid_leave_preview");
  if (
    input.attachmentRevisionIds.length > 3 ||
    input.attachmentRevisionIds.some((id) => !isUuid(id)) ||
    new Set(input.attachmentRevisionIds.map((id) => id.toLowerCase())).size !==
      input.attachmentRevisionIds.length
  )
    return failed("invalid_leave_attachments");
  return success(
    Object.freeze({
      ...selected.value,
      companyId: input.companyId,
      id: input.id.toLowerCase(),
      reason,
      previewRevision: input.previewRevision,
      attachmentRevisionIds: Object.freeze(
        input.attachmentRevisionIds.map((id) => id.toLowerCase()),
      ),
    }),
  );
}

export function prepareLeaveSubmission(
  access: CompanyAccess,
  operation: OperationId,
  id: string,
  review: ReviewedLeaveSelection,
  inputReason: string,
  evidence: readonly LeaveEvidenceOption[],
): Result<LeaveRequestSubmission> {
  if (!canPrepareLeave(access.permissions) || review.preview.companyId !== access.companyId)
    return failed("access_denied");
  const selection = validateLeaveSelection(review.selection);
  if (!selection.ok) return selection;
  if (
    !/^[0-9a-f]{64}$/u.test(review.preview.revision) ||
    review.preview.employeeId !== selection.value.employeeId ||
    review.preview.policy.typeId !== selection.value.typeId
  )
    return failed("invalid_leave_preview");
  if (
    evidence.length > 3 ||
    new Set(evidence.map((item) => item.revisionId)).size !== evidence.length ||
    evidence.some(
      (item) =>
        item.companyId !== access.companyId ||
        item.employeeId !== selection.value.employeeId ||
        !isUuid(item.revisionId),
    )
  )
    return failed("invalid_leave_attachments");
  if (review.preview.policy.attachmentRequired && evidence.length === 0)
    return failed("leave_attachment_required");
  return validateLeaveSubmission(operation, {
    ...selection.value,
    companyId: access.companyId,
    id,
    reason: inputReason,
    attachmentRevisionIds: evidence.map((item) => item.revisionId),
    previewRevision: review.preview.revision,
  });
}

export const leavePreparationAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "employee_not_found",
    "company_not_found",
  ].includes(failure.code);
export const leavePreparationChanged = (failure: Failure): boolean =>
  [
    "leave_preview_changed",
    "leave_overlap",
    "insufficient_leave_balance",
    "leave_year_closed",
    "payroll_period_frozen",
    "leave_type_unavailable",
    "leave_employee_ineligible",
    "leave_schedule_missing",
    "leave_ledger_conflict",
  ].includes(failure.code);
export const leaveSubmissionWasRejected = (failure: Failure): boolean =>
  leaveActionWasRejected(failure) ||
  leavePreparationAccessLost(failure) ||
  leavePreparationChanged(failure) ||
  [
    "invalid_leave_selection",
    "invalid_leave_submission",
    "invalid_leave_preview",
    "invalid_leave_attachments",
    "invalid_leave_days",
    "invalid_leave_request",
    "leave_range_too_large",
    "leave_attachment_required",
    "leave_attachment_unavailable",
    "no_leave_working_days",
    "leave_request_limit",
    "partial_leave_unavailable",
    "leave_duration_too_short",
    "leave_balance_limit",
  ].includes(failure.code);
