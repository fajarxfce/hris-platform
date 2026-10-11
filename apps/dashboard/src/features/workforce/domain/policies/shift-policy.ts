import { failed, type Result, success } from "../../../../core/domain/result";
import { type ShiftDetails, workModes } from "../entities/shift-definition";
import type { ShiftSearch } from "../entities/shift-search";

export const canReadShifts = (permissions: readonly string[]): boolean =>
  permissions.includes("workforce.read");
export const canManageShifts = (permissions: readonly string[]): boolean =>
  canReadShifts(permissions) && permissions.includes("workforce.manage");
export const isShiftCode = (value: string): boolean => /^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(value);
export const isShiftTime = (value: string): boolean => /^(?:[01]\d|2[0-3]):[0-5]\d$/u.test(value);

export function parseShiftSearch(input: ShiftSearch): Result<ShiftSearch> {
  if (input.query.length > 120 || (input.after !== null && !isShiftCode(input.after)))
    return failed("invalid_page");
  return success(Object.freeze({ query: input.query.trim(), after: input.after }));
}

export function validateShiftDetails(details: ShiftDetails): Result<ShiftDetails> {
  if (
    !isShiftCode(details.code) ||
    !details.name.trim() ||
    details.name.length > 200 ||
    !details.timezone.trim() ||
    details.timezone.length > 128 ||
    !workModes.includes(details.mode)
  )
    return failed("invalid_shift");
  if (!isShiftTime(details.startsAt) || !isShiftTime(details.endsAt))
    return failed("invalid_shift_duration");
  const start = Number(details.startsAt.slice(0, 2)) * 60 + Number(details.startsAt.slice(3));
  const end = Number(details.endsAt.slice(0, 2)) * 60 + Number(details.endsAt.slice(3));
  const minutes = (end - start + 1440) % 1440;
  if (
    minutes === 0 ||
    !Number.isSafeInteger(details.breakMinutes) ||
    details.breakMinutes < 0 ||
    details.breakMinutes >= minutes
  )
    return failed("invalid_shift_duration");
  if (
    !Number.isFinite(details.maxAccuracyMeters) ||
    details.maxAccuracyMeters < 1 ||
    details.maxAccuracyMeters > 10_000
  )
    return failed("invalid_location_accuracy");
  const fence = details.fence;
  if (
    fence &&
    (!details.locationRequired ||
      !Number.isFinite(fence.latitude) ||
      !Number.isFinite(fence.longitude) ||
      !Number.isFinite(fence.radiusMeters) ||
      Math.abs(fence.latitude) > 90 ||
      Math.abs(fence.longitude) > 180 ||
      fence.radiusMeters < 1 ||
      fence.radiusMeters > 100_000)
  )
    return failed("invalid_geofence");
  // IANA zone support and date-specific DST rules are authoritative on the server.
  return success(details);
}
