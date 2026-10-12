import { failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OvertimeActualTime } from "../entities/overtime-actual-time";
import type { OvertimeInterval } from "../entities/overtime-interval";
import { prepareOvertimeInterval } from "./overtime-interval-policy";

/** A submitted interval is UTC at whole-minute precision. Completion time is rechecked by the API. */
export function prepareOvertimeActual(
  requested: OvertimeInterval,
  input: OvertimeActualTime,
): Result<OvertimeActualTime> {
  const minute = 60_000_000n;
  const interval = prepareOvertimeInterval(input);
  if (!interval.ok) return interval;
  const start = utcInstantMicroseconds(input.startsAt),
    end = utcInstantMicroseconds(input.endsAt);
  if (start === null || end === null || end <= start) return failed("invalid_overtime_interval");
  const requestedStart = utcInstantMicroseconds(requested.startsAt),
    requestedEnd = utcInstantMicroseconds(requested.endsAt);
  if (
    requestedStart === null ||
    requestedEnd === null ||
    !Number.isInteger(requested.workedMinutes) ||
    requested.workedMinutes < 1 ||
    start < requestedStart ||
    end > requestedEnd ||
    (end - start) / minute - BigInt(input.breakMinutes) > BigInt(requested.workedMinutes)
  ) {
    return failed("overtime_outside_requested_window");
  }
  return success(
    Object.freeze({
      startsAt: input.startsAt,
      endsAt: input.endsAt,
      breakMinutes: input.breakMinutes,
    }),
  );
}
