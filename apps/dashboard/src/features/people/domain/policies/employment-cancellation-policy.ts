import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { EmploymentCancellation } from "../entities/employment-cancellation";
import type { EmploymentRevision } from "../entities/employment-revision";
import { canManageEmployment, employmentChangeWasRejected } from "./employment-policy";

export function canReviewEmploymentCancellation(
  permissions: readonly string[],
  revision: EmploymentRevision,
  companyDate: string,
): boolean {
  return (
    canManageEmployment(permissions) &&
    revision.revision > 0 &&
    revision.cancellation === null &&
    revision.terms.effectiveFrom > companyDate
  );
}

export function normalizeEmploymentCancellation(
  input: EmploymentCancellation,
): Result<EmploymentCancellation> {
  if (
    !isUuid(input.employeeId) ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 1 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !Number.isSafeInteger(input.revision) ||
    input.revision < 1 ||
    input.revision > input.expectedVersion
  )
    return failed("invalid_revision_cancellation");
  const reason = input.reason.trim();
  if (!reason || reason.length > 1000) return failed("reason_required");
  return success(Object.freeze({ ...input, reason }));
}

export function employmentCancellationWasRejected(failure: Failure): boolean {
  return (
    employmentChangeWasRejected(failure) ||
    [
      "invalid_revision_cancellation",
      "employment_revision_not_found",
      "effective_revision_cannot_be_cancelled",
      "revision_already_cancelled",
    ].includes(failure.code)
  );
}
