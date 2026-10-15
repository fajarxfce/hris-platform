import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  OffboardingSchedule,
  OffboardingScheduleAction,
} from "../entities/offboarding-schedule";
import type {
  OffboardingScheduleChange,
  OffboardingScheduleCreation,
} from "../entities/offboarding-schedule-command";
import type { OffboardingScheduleReview } from "../entities/offboarding-schedule-review";
import { lifecycleCaseChangeWasRejected } from "./lifecycle-case-change-policy";
import { canCompleteOffboarding, canReadOffboardingReview } from "./offboarding-policy";

export const offboardingScheduleAccessLost = (failure: Failure): boolean =>
  [
    "offboarding_access_required",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
  ].includes(failure.code);
export const offboardingScheduleChanged = (failure: Failure): boolean =>
  [
    "offboarding_schedule_changed",
    "offboarding_identity_changed",
    "offboarding_schedule_active",
    "offboarding_schedule_not_future",
    "offboarding_schedule_too_far",
    "offboarding_schedule_limit",
    "offboarding_schedule_finished",
    "offboarding_schedule_obsolete",
    "offboarding_schedule_not_found",
    "offboarding_job_not_stopped",
    "offboarding_job_result_invalid",
    "offboarding_revision_limit",
    "lifecycle_case_not_found",
    "lifecycle_case_not_open",
    "lifecycle_case_not_offboarding",
    "required_lifecycle_tasks_pending",
    "lifecycle_tasks_unresolved",
    "offboarding_terms_changed",
    "other_lifecycle_cases_pending",
    "scheduled_employment_changes_pending",
    "reporting_reassignment_required",
    "last_company_administrator",
    "employee_not_found",
    "stale_version",
    "stale_employment_version",
    "data_conflict",
    "job_already_finished",
    "job_cancellation_requested",
    "cannot_complete_own_offboarding",
    "cannot_change_own_offboarding",
  ].includes(failure.code);
export const offboardingScheduleCommandRejected = (failure: Failure): boolean =>
  lifecycleCaseChangeWasRejected(failure) ||
  offboardingScheduleChanged(failure) ||
  offboardingScheduleAccessLost(failure) ||
  ["invalid_offboarding_schedule", "job_queue_full"].includes(failure.code);
export const canScheduleOffboarding = (
  access: CompanyAccess,
  review: OffboardingScheduleReview,
): boolean =>
  canReadOffboardingReview(access.permissions) &&
  review.case.companyId === access.companyId &&
  review.case.kind === "OFFBOARDING" &&
  review.case.status === "OPEN" &&
  review.blockers.length === 0;
export const canChangeOffboardingSchedule = (
  access: CompanyAccess,
  details: OffboardingSchedule,
  action: OffboardingScheduleAction,
): boolean =>
  canCompleteOffboarding(access.permissions) &&
  details.companyId === access.companyId &&
  details.actions.includes(action);

export function normalizeOffboardingSchedule(
  input: OffboardingScheduleCreation,
): Result<OffboardingScheduleCreation> {
  const value = Object.freeze({
    ...input,
    id: input.id.toLowerCase() as OffboardingScheduleCreation["id"],
    caseId: input.caseId.toLowerCase() as OffboardingScheduleCreation["caseId"],
    reason: input.reason.trim(),
  });
  if (
    !isUuid(value.id) ||
    !isUuid(value.caseId) ||
    !/^[a-f0-9]{64}$/u.test(value.basisRevision) ||
    ![value.expectedVersion, value.employmentVersion].every(
      (v) => Number.isSafeInteger(v) && v >= 0 && v < Number.MAX_SAFE_INTEGER,
    ) ||
    value.reason.length === 0 ||
    value.reason.length > 1000
  )
    return failed("invalid_offboarding_schedule");
  return success(value);
}
export function normalizeOffboardingScheduleChange(
  input: OffboardingScheduleChange,
): Result<OffboardingScheduleChange> {
  const value = Object.freeze({
    ...input,
    id: input.id.toLowerCase() as OffboardingScheduleChange["id"],
    caseId: input.caseId.toLowerCase() as OffboardingScheduleChange["caseId"],
    jobId: input.jobId.toLowerCase() as OffboardingScheduleChange["jobId"],
    reason: input.reason.trim(),
  });
  if (
    !isUuid(value.id) ||
    !isUuid(value.caseId) ||
    !isUuid(value.jobId) ||
    value.expectedVersion !== 0 ||
    !Number.isSafeInteger(value.expectedJobVersion) ||
    value.expectedJobVersion < 0 ||
    value.expectedJobVersion >= Number.MAX_SAFE_INTEGER ||
    value.reason.length === 0 ||
    value.reason.length > 1000
  )
    return failed("invalid_offboarding_schedule");
  return success(value);
}
