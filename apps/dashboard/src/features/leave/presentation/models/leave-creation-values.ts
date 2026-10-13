import { failed, type Result, success } from "../../../../core/domain/result";
import { leavePortions } from "../../domain/entities/leave-day";
import type { RequestedLeaveDay } from "../../domain/entities/leave-request-selection";
import { requestedLeaveRange } from "../../domain/policies/leave-preparation-policy";

export type LeaveCreationValues = {
  from: string;
  until: string;
  firstPortion: string;
  lastPortion: string;
  reason: string;
};

export function selectedLeaveDays(
  values: LeaveCreationValues,
): Result<readonly RequestedLeaveDay[]> {
  const range = requestedLeaveRange(values.from, values.until, "FULL");
  if (!range.ok) return range;
  if (
    ![values.firstPortion, values.lastPortion].every((part) =>
      leavePortions.some((value) => value === part),
    )
  )
    return failed("invalid_leave_selection");
  return success(
    Object.freeze(
      range.value.map((day, index) =>
        Object.freeze({
          workDate: day.workDate,
          portion: (index === 0
            ? values.firstPortion
            : index === range.value.length - 1
              ? values.lastPortion
              : "FULL") as RequestedLeaveDay["portion"],
        }),
      ),
    ),
  );
}
