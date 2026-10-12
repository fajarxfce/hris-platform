import { failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OvertimeIntervalInput } from "../entities/overtime-interval";

/** Requested and actual time share whole-minute, elapsed-time and break limits. */
export function prepareOvertimeInterval(
  input: OvertimeIntervalInput,
): Result<OvertimeIntervalInput> {
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
  const minute = 60_000_000n;
  if (
    start === null ||
    end === null ||
    end - start < minute ||
    end - start > 720n * minute ||
    BigInt(input.breakMinutes) * minute >= end - start
  )
    return failed("invalid_overtime_interval");
  return success(
    Object.freeze({
      startsAt: input.startsAt,
      endsAt: input.endsAt,
      breakMinutes: input.breakMinutes,
    }),
  );
}
