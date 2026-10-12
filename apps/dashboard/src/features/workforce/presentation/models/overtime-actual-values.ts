import {
  companyDateTimeInput,
  parseCompanyDateTime,
} from "../../../../core/presentation/dates/company-date-time";
import type { OvertimeActualTime } from "../../domain/entities/overtime-actual-time";
import type { OvertimeRequestDetails } from "../../domain/entities/overtime-request-details";

export type OvertimeActionValues = {
  startsAt: string;
  endsAt: string;
  breakMinutes: string;
  reason: string;
};

export function overtimeActionValues(review: OvertimeRequestDetails | null): OvertimeActionValues {
  const request = review?.request;
  return {
    startsAt: request
      ? companyDateTimeInput(request.requested.startsAt, request.timezone).slice(0, 16)
      : "",
    endsAt: request
      ? companyDateTimeInput(request.requested.endsAt, request.timezone).slice(0, 16)
      : "",
    breakMinutes: String(request?.requested.breakMinutes ?? 0),
    reason: "",
  };
}

/** Convert editor text in the request's zone; duration and eligibility remain domain policy. */
export function parseOvertimeActualValues(
  fields: OvertimeActionValues,
  review: OvertimeRequestDetails,
): OvertimeActualTime | null {
  const { requested, timezone } = review.request;
  const startsAt = parseCompanyDateTime(fields.startsAt, timezone, requested.startsAt);
  const endsAt = parseCompanyDateTime(fields.endsAt, timezone, requested.endsAt);
  if (startsAt === null || endsAt === null || !/^\d{1,3}$/u.test(fields.breakMinutes)) return null;
  return { startsAt, endsAt, breakMinutes: Number(fields.breakMinutes) };
}
