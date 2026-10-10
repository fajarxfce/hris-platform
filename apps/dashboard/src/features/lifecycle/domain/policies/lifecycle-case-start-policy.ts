import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { canReadEmployees } from "../../../people/domain/policies/employee-policy";
import type { LifecycleCaseStart } from "../entities/lifecycle-case-start";
import {
  canManageLifecycle,
  canReadLifecycle,
  isLifecycleTaskKey,
} from "./lifecycle-template-policy";

export const canPrepareLifecycleCase = (permissions: readonly string[]): boolean =>
  canManageLifecycle(permissions) && canReadLifecycle(permissions) && canReadEmployees(permissions);

export function normalizeLifecycleCaseStart(input: LifecycleCaseStart): Result<LifecycleCaseStart> {
  const assignees = Object.entries(input.assignees);
  if (
    !isUuid(input.id) ||
    !isUuid(input.employmentId) ||
    !isUuid(input.templateId) ||
    !Number.isSafeInteger(input.templateVersion) ||
    input.templateVersion < 0 ||
    !isCalendarDate(input.targetDate) ||
    input.targetDate < "1900-01-01" ||
    input.targetDate > "2200-12-31" ||
    assignees.length > 64 ||
    assignees.some(([key, account]) => !isLifecycleTaskKey(key) || !isUuid(account)) ||
    input.reason.trim().length === 0 ||
    input.reason.trim().length > 1000
  )
    return failed("invalid_lifecycle_case");
  return success(
    Object.freeze({
      id: input.id.toLowerCase(),
      employmentId: input.employmentId.toLowerCase(),
      templateId: input.templateId.toLowerCase(),
      templateVersion: input.templateVersion,
      targetDate: input.targetDate,
      assignees: Object.freeze(
        Object.fromEntries(assignees.map(([key, account]) => [key, account.toLowerCase()])),
      ),
      reason: input.reason.trim(),
    }),
  );
}

/** These responses establish rejection only when no earlier attempt had an ambiguous outcome. */
export const lifecycleCaseStartWasRejected = (failure: Failure): boolean =>
  [
    "invalid_lifecycle_case",
    "invalid_lifecycle_assignee",
    "lifecycle_assignee_unavailable",
    "lifecycle_template_unavailable",
    "stale_template_version",
    "employment_offboarded",
    "employee_not_found",
    "employment_unavailable",
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
