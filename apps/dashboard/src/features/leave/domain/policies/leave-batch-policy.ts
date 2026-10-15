import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { type LeaveBatchId, type LeaveBatchKind, leaveBatchKinds } from "../entities/leave-batch";
import type { LeaveBatchCreation, LeaveBatchResume } from "../entities/leave-batch-command";
import type { LeaveBatchDetails } from "../entities/leave-batch-details";
import type { LeaveBatchSelection } from "../entities/leave-batch-review";
import { leavePreparationAccessLost } from "./leave-preparation-policy";
import { leaveActionWasRejected } from "./leave-request-action-policy";
export const canReadLeaveBatches = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.read");
export const canRunLeaveBatch = (permissions: readonly string[], kind: LeaveBatchKind): boolean =>
  canReadLeaveBatches(permissions) &&
  permissions.includes(kind === "ACCRUAL" ? "leave.accrual.post" : "leave.year.close");
export const isLeaveBatchPeriod = (period: string): boolean =>
  /^(?:19\d{2}|20\d{2}|21\d{2})-(?:0[1-9]|1[0-2])$/u.test(period);
export const isLeaveBatchResultCursor = (after: string | null): boolean =>
  after === null || (/^(?:0|[1-9]\d{0,3})$/u.test(after) && Number(after) <= 5000);
export function normalizeLeaveBatchSelection(
  input: LeaveBatchSelection,
): Result<LeaveBatchSelection> {
  if (
    !leaveBatchKinds.includes(input.kind) ||
    !isUuid(input.typeId) ||
    !isLeaveBatchPeriod(input.period) ||
    (input.kind === "YEAR_CLOSE" && !input.period.endsWith("-12"))
  )
    return failed("invalid_leave_batch");
  return success(
    Object.freeze({ kind: input.kind, typeId: input.typeId.toLowerCase(), period: input.period }),
  );
}
export function normalizeLeaveBatchCreation(input: LeaveBatchCreation): Result<LeaveBatchCreation> {
  const selection = normalizeLeaveBatchSelection(input);
  if (!selection.ok) return selection;
  const reason = input.reason.trim();
  if (
    !isUuid(input.id) ||
    !isUuid(input.companyId) ||
    !Number.isSafeInteger(input.expectedPolicyVersion) ||
    input.expectedPolicyVersion < 0 ||
    input.employeeIds.length < 1 ||
    input.employeeIds.length > 5000 ||
    input.employeeIds.some((id) => !isUuid(id)) ||
    new Set(input.employeeIds.map((id) => id.toLowerCase())).size !== input.employeeIds.length ||
    !reason ||
    reason.length > 1000
  )
    return failed("invalid_leave_batch");
  return success(
    Object.freeze({
      ...input,
      ...selection.value,
      id: input.id.toLowerCase() as LeaveBatchId,
      employeeIds: Object.freeze(input.employeeIds.map((id) => id.toLowerCase())),
      reason,
    }),
  );
}
export function normalizeLeaveBatchResume(input: LeaveBatchResume): Result<LeaveBatchResume> {
  const reason = input.reason.trim();
  if (
    !isUuid(input.id) ||
    !isUuid(input.companyId) ||
    !leaveBatchKinds.includes(input.kind) ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000
  )
    return failed("invalid_leave_batch");
  return success(Object.freeze({ ...input, id: input.id.toLowerCase() as LeaveBatchId, reason }));
}
export const canResumeLeaveBatch = (access: CompanyAccess, details: LeaveBatchDetails): boolean =>
  details.batch.companyId === access.companyId &&
  canRunLeaveBatch(access.permissions, details.batch.kind) &&
  details.actions.includes("RESUME");
export const leaveBatchAccessLost = leavePreparationAccessLost;
export const leaveBatchReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "stale_policy_version",
    "leave_batch_policy_changed",
    "leave_batch_active",
    "leave_batch_not_stopped",
    "leave_batch_not_found",
    "leave_batch_job_obsolete",
    "leave_batch_changed",
    "leave_batch_checkpoint_inconsistent",
    "leave_batch_attempt_limit",
    "leave_batch_employee_unavailable",
    "leave_batch_employee_limit",
    "leave_type_unavailable",
    "leave_type_not_found",
    "leave_accrual_not_due",
    "leave_accrual_not_configured",
    "invalid_leave_accrual_policy",
    "leave_year_not_ended",
  ].includes(failure.code);
export const leaveBatchCommandRejected = (failure: Failure): boolean =>
  leaveActionWasRejected(failure) ||
  leaveBatchAccessLost(failure) ||
  leaveBatchReviewChanged(failure) ||
  ["invalid_leave_batch", "leave_batch_resume_owner_required", "job_queue_full"].includes(
    failure.code,
  );
