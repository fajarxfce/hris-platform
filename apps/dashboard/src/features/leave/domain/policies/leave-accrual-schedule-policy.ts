import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type {
  LeaveAccrualScheduleFrequency,
  LeaveAccrualScheduleId,
} from "../entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleCreation } from "../entities/leave-accrual-schedule-command";
import type { LeaveAccrualScheduleSelection } from "../entities/leave-accrual-schedule-review";
import {
  canRunLeaveBatch,
  isLeaveBatchPeriod,
  leaveBatchCommandRejected,
  leaveBatchReviewChanged,
} from "./leave-batch-policy";
export const canScheduleLeaveAccrual = (permissions: readonly string[]): boolean =>
  canRunLeaveBatch(permissions, "ACCRUAL");
export function normalizeLeaveAccrualScheduleSelection(
  input: LeaveAccrualScheduleSelection,
): Result<LeaveAccrualScheduleSelection> {
  if (
    !isUuid(input.typeId) ||
    !isLeaveBatchPeriod(input.firstPeriod) ||
    !Number.isInteger(input.periods) ||
    input.periods < 1 ||
    input.periods > 12
  )
    return failed("invalid_leave_accrual_schedule");
  return success(
    Object.freeze({
      typeId: input.typeId.toLowerCase(),
      firstPeriod: input.firstPeriod,
      periods: input.periods,
    }),
  );
}
/** Calendar arithmetic for an already bounded review; no local timezone or clock is consulted. */
export function leaveAccrualSchedulePeriod(
  first: string,
  ordinal: number,
  frequency: LeaveAccrualScheduleFrequency,
): string {
  const total =
    Number(first.slice(0, 4)) * 12 +
    Number(first.slice(5, 7)) -
    1 +
    ordinal * (frequency === "ANNUAL" ? 12 : 1);
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, "0")}`;
}
export function normalizeLeaveAccrualScheduleCreation(
  input: LeaveAccrualScheduleCreation,
): Result<LeaveAccrualScheduleCreation> {
  const selected = normalizeLeaveAccrualScheduleSelection(input);
  if (!selected.ok) return selected;
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
    return failed("invalid_leave_accrual_schedule");
  return success(
    Object.freeze({
      ...input,
      ...selected.value,
      id: input.id.toLowerCase() as LeaveAccrualScheduleId,
      employeeIds: Object.freeze(input.employeeIds.map((id) => id.toLowerCase())),
      reason,
    }),
  );
}
export const leaveAccrualScheduleReviewChanged = (failure: Failure): boolean =>
  leaveBatchReviewChanged(failure) ||
  ["leave_schedule_frequency_changed", "leave_accrual_schedule_not_found"].includes(failure.code);
export const leaveAccrualScheduleCommandRejected = (failure: Failure): boolean =>
  leaveBatchCommandRejected(failure) ||
  leaveAccrualScheduleReviewChanged(failure) ||
  failure.code === "invalid_leave_accrual_schedule";
