import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCarryExpiryId } from "../entities/leave-carry-expiry";
import type { LeaveCarryExpiryDetails } from "../entities/leave-carry-expiry-details";
import type { LeaveCarryExpiryResume } from "../entities/leave-carry-expiry-resume";
import { leavePreparationAccessLost } from "./leave-preparation-policy";
import { leaveActionWasRejected } from "./leave-request-action-policy";
export const canReadLeaveCarryExpiries = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.read");
export const isLeaveCarryExpiryCursor = (after: string | null): boolean =>
  after === null || (/^[1-9][0-9]{0,3}$/u.test(after) && Number(after) <= 5000);
export const canResumeLeaveCarryExpiry = (
  access: CompanyAccess,
  details: LeaveCarryExpiryDetails,
): boolean =>
  details.expiry.companyId === access.companyId &&
  canReadLeaveCarryExpiries(access.permissions) &&
  access.permissions.includes("leave.year.close") &&
  details.actions.includes("RESUME") &&
  details.expiry.version < 7 &&
  ["FAILED", "CANCELLED"].includes(details.job.status);
export function normalizeLeaveCarryExpiryResume(
  input: LeaveCarryExpiryResume,
): Result<LeaveCarryExpiryResume> {
  const reason = input.reason.trim();
  if (
    !isUuid(input.id) ||
    !isUuid(input.companyId) ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion > 7 ||
    !reason ||
    reason.length > 1000
  )
    return failed("invalid_leave_carry_expiry_recovery");
  return success(
    Object.freeze({ ...input, id: input.id.toLowerCase() as LeaveCarryExpiryId, reason }),
  );
}
export const leaveCarryExpiryAccessLost = leavePreparationAccessLost;
export const leaveCarryExpiryReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "leave_carry_expiry_not_found",
    "leave_carry_expiry_changed",
    "leave_carry_expiry_job_obsolete",
    "leave_carry_expiry_not_stopped",
    "leave_carry_expiry_checkpoint_inconsistent",
    "leave_carry_expiry_attempt_limit",
  ].includes(failure.code);
export const leaveCarryExpiryCommandRejected = (failure: Failure): boolean =>
  leaveActionWasRejected(failure) ||
  leaveCarryExpiryAccessLost(failure) ||
  leaveCarryExpiryReviewChanged(failure) ||
  ["invalid_leave_carry_expiry_recovery", "job_queue_full"].includes(failure.code);
