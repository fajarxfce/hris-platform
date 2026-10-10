import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCase } from "../entities/lifecycle-case";
import type { OffboardingCompletion } from "../entities/offboarding-completion";
import type { OffboardingReview } from "../entities/offboarding-review";
import {
  lifecycleCaseChangeWasRejected,
  normalizeLifecycleCaseChange,
} from "./lifecycle-case-change-policy";

export const canCompleteOffboarding = (permissions: readonly string[]): boolean =>
  ["people.lifecycle.manage", "people.manage", "people.offboard"].every((p) =>
    permissions.includes(p),
  );
export const canReadOffboardingReview = (permissions: readonly string[]): boolean =>
  canCompleteOffboarding(permissions) && permissions.includes("people.lifecycle.read");
export const canReviewOffboardingCase = (access: CompanyAccess, details: LifecycleCase): boolean =>
  canReadOffboardingReview(access.permissions) &&
  details.companyId === access.companyId &&
  details.kind === "OFFBOARDING" &&
  details.status === "OPEN";

/** Known checklist/date constraints; the server evaluates remaining employment/access policy. */
export function validateOffboardingReview(review: OffboardingReview): Result<void> {
  const details = review.case;
  if (details.kind !== "OFFBOARDING" || details.status !== "OPEN")
    return failed("lifecycle_case_not_open");
  if (
    details.version === Number.MAX_SAFE_INTEGER ||
    review.employmentVersion === Number.MAX_SAFE_INTEGER
  )
    return failed("invalid_offboarding");
  if (details.tasks.some((task) => task.required && task.status !== "DONE"))
    return failed("required_lifecycle_tasks_pending");
  if (details.tasks.some((task) => task.status === "PENDING"))
    return failed("lifecycle_tasks_unresolved");
  if (details.targetDate >= review.today) return failed("offboarding_date_not_reached");
  return success(undefined);
}
export function normalizeOffboardingCompletion(
  input: OffboardingCompletion,
): Result<OffboardingCompletion> {
  const change = normalizeLifecycleCaseChange(input);
  if (
    !change.ok ||
    !Number.isSafeInteger(input.employmentVersion) ||
    input.employmentVersion < 0 ||
    input.employmentVersion === Number.MAX_SAFE_INTEGER
  )
    return failed("invalid_offboarding");
  return success(Object.freeze({ ...change.value, employmentVersion: input.employmentVersion }));
}
export const offboardingCompletionWasRejected = (failure: Failure): boolean =>
  lifecycleCaseChangeWasRejected(failure) ||
  [
    "offboarding_access_required",
    "invalid_offboarding",
    "stale_employment_version",
    "offboarding_terms_changed",
    "other_lifecycle_cases_pending",
    "offboarding_date_not_reached",
    "cannot_complete_own_offboarding",
    "scheduled_employment_changes_pending",
    "reporting_reassignment_required",
    "last_company_administrator",
    "employee_not_found",
  ].includes(failure.code);
