import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { OvertimeInterval } from "../../domain/entities/overtime-interval";
import type { OvertimeIntervalDto } from "../models/overtime-request-dto";

export function toOvertimeInterval(dto: OvertimeIntervalDto): OvertimeInterval {
  const start = utcInstantMicroseconds(dto.startsAt),
    end = utcInstantMicroseconds(dto.endsAt);
  if (
    start === null ||
    end === null ||
    !/:00(?:\.0{1,9})?Z$/u.test(dto.startsAt) ||
    !/:00(?:\.0{1,9})?Z$/u.test(dto.endsAt) ||
    end <= start ||
    end - start > 720n * 60_000_000n ||
    end - start !== BigInt(dto.workedMinutes + dto.breakMinutes) * 60_000_000n
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}
