import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  WorkPeriodActionContext,
  WorkPeriodIntent,
} from "../entities/work-period-action-context";
import type { WorkPeriodCommand } from "../entities/work-period-command";
import type { WorkPeriodOverview } from "../entities/work-period-overview";
import { isWorkPeriodMonth } from "./work-period-policy";

export function reviewWorkPeriodAction(
  overview: WorkPeriodOverview,
  intent: WorkPeriodIntent,
): Result<WorkPeriodActionContext> {
  if (intent === "close" && overview.month >= overview.companyDate.slice(0, 7))
    return failed("work_period_not_ended");
  if (!overview.availableActions.includes(intent))
    return failed(intent === "close" ? "work_period_locked" : "work_period_recovery_unavailable");
  return success(
    Object.freeze({
      companyId: overview.companyId,
      month: overview.month,
      companyDate: overview.companyDate,
      periodId: overview.period?.id ?? null,
      version: overview.period?.version ?? 0,
      intent,
    }),
  );
}

export function prepareWorkPeriodCommand(
  access: CompanyAccess,
  operation: OperationId,
  context: WorkPeriodActionContext,
  intent: WorkPeriodIntent,
  inputReason: string,
): Result<WorkPeriodCommand> {
  if (context.companyId !== access.companyId || !access.permissions.includes("workforce.close"))
    return failed("access_denied");
  const reason = inputReason.trim();
  if (
    context.intent !== intent ||
    !isUuid(operation) ||
    !Number.isSafeInteger(context.version) ||
    context.version < 0 ||
    context.version >= Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000 ||
    (context.periodId !== null && !isUuid(context.periodId)) ||
    (context.version > 0 && context.periodId === null) ||
    (intent === "recover" && context.periodId === null)
  )
    return failed(
      intent === "close" ? "invalid_work_period_close" : "invalid_work_period_recovery",
    );
  if (!isWorkPeriodMonth(context.month) || !isCalendarDate(context.companyDate))
    return failed("invalid_work_period_range");
  // Retry uses the original reviewed date; current eligibility is checked by the API after receipt lookup.
  if (intent === "close" && context.month >= context.companyDate.slice(0, 7))
    return failed("work_period_not_ended");
  return success(
    Object.freeze({
      month: context.month,
      periodId: context.periodId,
      expectedVersion: context.version,
      reason,
    }),
  );
}

export const workPeriodReadDenied = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_required",
    "company_not_found",
    "session_revoked",
    "unauthenticated",
    "authentication_required",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "employee_not_found",
    "work_period_snapshot_unavailable",
  ].includes(failure.code);

export const workPeriodReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "work_period_locked",
    "work_period_recovery_unavailable",
    "work_period_not_ended",
    "overtime_resolution_required",
    "work_period_employee_limit",
    "job_queue_full",
  ].includes(failure.code);

export const workPeriodCommandRejected = (failure: Failure): boolean =>
  workPeriodReadDenied(failure) ||
  workPeriodReviewChanged(failure) ||
  [
    "invalid_work_period_close",
    "invalid_work_period_recovery",
    "invalid_work_period_range",
    "data_conflict",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
