import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OnboardingSchedule, OnboardingScheduleAction } from "../entities/onboarding-schedule";
import type {
  OnboardingScheduleChange,
  OnboardingScheduleCreation,
} from "../entities/onboarding-schedule-command";
import type { OnboardingScheduleReview } from "../entities/onboarding-schedule-review";
import type { OnboardingScheduleSelection } from "../entities/onboarding-schedule-selection";
import { lifecycleCaseChangeWasRejected } from "./lifecycle-case-change-policy";
import { isLifecycleTaskKey } from "./lifecycle-template-policy";

export const canReadOnboardingSchedules = (permissions: readonly string[]): boolean =>
  ["people.read", "people.lifecycle.read", "people.lifecycle.manage"].every((p) =>
    permissions.includes(p),
  );
export const canManageOnboardingSchedules = (permissions: readonly string[]): boolean =>
  permissions.includes("people.lifecycle.manage");
export const onboardingScheduleAccessLost = (failure: Failure): boolean =>
  [
    "onboarding_access_required",
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
export const onboardingScheduleChanged = (failure: Failure): boolean =>
  [
    "onboarding_schedule_changed",
    "onboarding_schedule_active",
    "onboarding_schedule_not_future",
    "onboarding_schedule_too_far",
    "onboarding_schedule_limit",
    "onboarding_schedule_finished",
    "onboarding_schedule_obsolete",
    "onboarding_schedule_not_found",
    "onboarding_job_not_stopped",
    "onboarding_job_result_invalid",
    "onboarding_revision_limit",
    "onboarding_template_unavailable",
    "onboarding_terms_changed",
    "onboarding_already_completed",
    "onboarding_schedule_review_required",
    "lifecycle_template_not_found",
    "lifecycle_case_exists",
    "other_lifecycle_cases_pending",
    "employment_offboarded",
    "employee_not_found",
    "stale_version",
    "stale_employment_version",
    "stale_template_version",
    "data_conflict",
    "job_already_finished",
    "job_cancellation_requested",
    "lifecycle_assignee_unavailable",
    "invalid_lifecycle_assignee",
  ].includes(failure.code);
export const onboardingScheduleCommandRejected = (failure: Failure): boolean =>
  lifecycleCaseChangeWasRejected(failure) ||
  onboardingScheduleChanged(failure) ||
  onboardingScheduleAccessLost(failure) ||
  ["invalid_onboarding_schedule", "job_queue_full"].includes(failure.code);
export const canScheduleOnboarding = (
  access: CompanyAccess,
  review: OnboardingScheduleReview,
): boolean =>
  canReadOnboardingSchedules(access.permissions) &&
  review.companyId === access.companyId &&
  review.template.kind === "ONBOARDING" &&
  review.template.active &&
  review.blockers.length === 0;
export const canChangeOnboardingSchedule = (
  access: CompanyAccess,
  details: OnboardingSchedule,
  action: OnboardingScheduleAction,
): boolean =>
  canManageOnboardingSchedules(access.permissions) &&
  details.companyId === access.companyId &&
  details.actions.includes(action);

export function normalizeOnboardingSelection(
  input: OnboardingScheduleSelection,
): Result<OnboardingScheduleSelection> {
  if (
    !isUuid(input.templateId) ||
    ![input.targetDate, input.startsOn].every(
      (d) => isCalendarDate(d) && d >= "1900-01-01" && d <= "2200-12-31",
    ) ||
    input.startsOn > input.targetDate
  )
    return failed("invalid_onboarding_schedule");
  return success(
    Object.freeze({
      templateId: input.templateId.toLowerCase() as OnboardingScheduleSelection["templateId"],
      targetDate: input.targetDate,
      startsOn: input.startsOn,
    }),
  );
}
export function normalizeOnboardingSchedule(
  input: OnboardingScheduleCreation,
): Result<OnboardingScheduleCreation> {
  const selection = normalizeOnboardingSelection(input);
  if (!selection.ok) return selection;
  const assignees = Object.entries(input.assignees);
  const reason = input.reason.trim();
  if (
    ![input.id, input.caseId, input.employmentId].every(isUuid) ||
    !/^[a-f0-9]{64}$/u.test(input.basisRevision) ||
    ![input.employmentVersion, input.templateVersion].every(
      (v) => Number.isSafeInteger(v) && v >= 0 && v < Number.MAX_SAFE_INTEGER,
    ) ||
    assignees.length > 64 ||
    assignees.some(([key, id]) => !isLifecycleTaskKey(key) || !isUuid(id)) ||
    reason.length === 0 ||
    reason.length > 1000
  )
    return failed("invalid_onboarding_schedule");
  return success(
    Object.freeze({
      ...input,
      ...selection.value,
      id: input.id.toLowerCase() as OnboardingScheduleCreation["id"],
      caseId: input.caseId.toLowerCase() as OnboardingScheduleCreation["caseId"],
      employmentId: input.employmentId.toLowerCase() as OnboardingScheduleCreation["employmentId"],
      assignees: Object.freeze(
        Object.fromEntries(assignees.map(([key, id]) => [key, id.toLowerCase() as typeof id])),
      ),
      reason,
    }),
  );
}
export function normalizeOnboardingScheduleChange(
  input: OnboardingScheduleChange,
): Result<OnboardingScheduleChange> {
  const reason = input.reason.trim();
  if (
    ![input.id, input.employmentId, input.jobId].every(isUuid) ||
    input.expectedVersion !== 0 ||
    !Number.isSafeInteger(input.expectedJobVersion) ||
    input.expectedJobVersion < 0 ||
    input.expectedJobVersion >= Number.MAX_SAFE_INTEGER ||
    reason.length === 0 ||
    reason.length > 1000
  )
    return failed("invalid_onboarding_schedule");
  return success(
    Object.freeze({
      ...input,
      id: input.id.toLowerCase() as OnboardingScheduleChange["id"],
      employmentId: input.employmentId.toLowerCase() as OnboardingScheduleChange["employmentId"],
      jobId: input.jobId.toLowerCase() as OnboardingScheduleChange["jobId"],
      reason,
    }),
  );
}
