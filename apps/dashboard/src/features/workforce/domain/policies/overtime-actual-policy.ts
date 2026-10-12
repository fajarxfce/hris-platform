import { failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OvertimeActualTime } from "../entities/overtime-actual-time";
import type { OvertimeInterval } from "../entities/overtime-interval";

/** A submitted interval is UTC at whole-minute precision. Completion time is rechecked by the API. */
export function prepareOvertimeActual(
  requested: OvertimeInterval,
  input: OvertimeActualTime,
): Result<OvertimeActualTime> {
  const minute = 60_000_000n;
  const wholeMinute = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00(?:\.0{1,9})?Z$/u;
  if (
    !wholeMinute.test(input.startsAt) ||
    !wholeMinute.test(input.endsAt) ||
    !Number.isInteger(input.breakMinutes) ||
    input.breakMinutes < 0 ||
    input.breakMinutes > 120
  )
    return failed("invalid_overtime_interval");
  const start = utcInstantMicroseconds(input.startsAt),
    end = utcInstantMicroseconds(input.endsAt);
  if (
    start === null ||
    end === null ||
    end - start < minute ||
    end - start > 720n * minute ||
    BigInt(input.breakMinutes) * minute >= end - start
  )
    return failed("invalid_overtime_interval");
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
