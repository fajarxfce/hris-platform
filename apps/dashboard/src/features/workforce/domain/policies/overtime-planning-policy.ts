import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import { zonedCalendarDate } from "../../../../core/domain/zoned-calendar-date";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { isEmployeeNumber } from "../../../people/domain/policies/employee-policy";
import type { OvertimeIntervalInput } from "../entities/overtime-interval";
import type { OvertimePlan } from "../entities/overtime-plan";
import type { OvertimePlanContext } from "../entities/overtime-plan-context";
import type { OvertimePlanningSearch } from "../entities/overtime-planning-search";
import type { OvertimeRequestId } from "../entities/overtime-request";
import { overtimeActionReadDenied } from "./overtime-action-policy";
import { prepareOvertimeInterval } from "./overtime-interval-policy";

export const canPlanOvertime = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["overtime.manage", "overtime.self.manage"].includes(permission),
  );

export const isOvertimeWorkDate = (date: string): boolean =>
  isCalendarDate(date) && date >= "2000-01-01" && date <= "2100-12-31";

export function isOvertimePlanningDate(date: string, companyDate: string): boolean {
  if (!isOvertimeWorkDate(date) || !isCalendarDate(companyDate)) return false;
  const days =
    (Date.parse(`${date}T00:00:00Z`) - Date.parse(`${companyDate}T00:00:00Z`)) / 86_400_000;
  return days >= -31 && days <= 90;
}

export function parseOvertimePlanningSearch(
  input: OvertimePlanningSearch,
): Result<OvertimePlanningSearch> {
  if (!isOvertimeWorkDate(input.workDate)) return failed("overtime_date_out_of_range");
  if (input.query.length > 120 || (input.after !== null && !isEmployeeNumber(input.after)))
    return failed("invalid_page");
  return success(Object.freeze({ ...input, query: input.query.trim() }));
}

export function prepareOvertimePlan(
  access: CompanyAccess,
  operation: OperationId,
  id: string,
  review: OvertimePlanContext,
  input: OvertimeIntervalInput,
  inputReason: string,
): Result<OvertimePlan> {
  if (access.companyId !== review.companyId || !canPlanOvertime(access.permissions))
    return failed("access_denied");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(id) ||
    !isUuid(review.employeeId) ||
    !Number.isSafeInteger(review.employmentVersion) ||
    review.employmentVersion < 0 ||
    !reason ||
    reason.length > 1000 ||
    !/^[0-9a-f]{64}$/u.test(review.revision)
  )
    return failed("invalid_overtime_request");
  if (!isOvertimePlanningDate(review.workDate, review.companyDate))
    return failed("overtime_date_out_of_range");
  const requested = prepareOvertimeInterval(input);
  if (!requested.ok) return requested;
  const start = Date.parse(input.startsAt),
    end = Date.parse(input.endsAt);
  const nextDay = new Date(Date.parse(`${review.workDate}T00:00:00Z`) + 86_400_000)
    .toISOString()
    .slice(0, 10);
  try {
    const first = zonedCalendarDate(review.timezone, new Date(start));
    // The interval's endpoint is exclusive, including at local midnight.
    const last = zonedCalendarDate(review.timezone, new Date(end - 1));
    if (
      first < review.workDate ||
      last > nextDay ||
      (review.schedule.kind === "OFF" && first !== review.workDate)
    )
      return failed("overtime_work_date_mismatch");
  } catch {
    return failed("invalid_overtime_request");
  }
  if (review.schedule.kind === "WORK") {
    const shiftStart = utcInstantMicroseconds(review.schedule.startsAt),
      shiftEnd = utcInstantMicroseconds(review.schedule.endsAt);
    if (shiftStart === null || shiftEnd === null || shiftEnd <= shiftStart)
      return failed("invalid_overtime_request");
    if (BigInt(start) * 1000n < shiftEnd && BigInt(end) * 1000n > shiftStart)
      return failed("overtime_overlaps_shift");
  }
  return success(
    Object.freeze({
      id: id.toLowerCase() as OvertimeRequestId,
      employeeId: review.employeeId,
      expectedEmploymentVersion: review.employmentVersion,
      workDate: review.workDate,
      requested: requested.value,
      reason,
      planningRevision: review.revision,
    }),
  );
}

export const overtimePlanningReadDenied = (failure: Failure): boolean =>
  overtimeActionReadDenied(failure) || failure.code === "employee_not_found";

export const overtimePlanningReviewChanged = (failure: Failure): boolean =>
  [
    "stale_employment_version",
    "overtime_planning_changed",
    "work_period_locked",
    "overtime_employment_inactive",
    "overtime_schedule_missing",
    "overtime_month_capacity",
  ].includes(failure.code);

export const overtimePlanWasRejected = (failure: Failure): boolean =>
  overtimePlanningReadDenied(failure) ||
  overtimePlanningReviewChanged(failure) ||
  [
    "invalid_overtime_request",
    "invalid_overtime_interval",
    "invalid_overtime_planning_revision",
    "overtime_date_out_of_range",
    "overtime_work_date_mismatch",
    "overtime_overlaps_shift",
    "overtime_overlap",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
