import { parseCompanyDateTime } from "../../../../core/presentation/dates/company-date-time";
import type { OvertimeIntervalInput } from "../../domain/entities/overtime-interval";

export type OvertimePlanValues = {
  startsAt: string;
  endsAt: string;
  breakMinutes: string;
  reason: string;
};
export const emptyOvertimePlanValues = (): OvertimePlanValues => ({
  startsAt: "",
  endsAt: "",
  breakMinutes: "0",
  reason: "",
});

/** Editor conversion only; domain validates duration, work date and shift overlap. */
export function parseOvertimePlanValues(
  fields: OvertimePlanValues,
  timezone: string,
): OvertimeIntervalInput | null {
  const startsAt = parseCompanyDateTime(fields.startsAt, timezone),
    endsAt = parseCompanyDateTime(fields.endsAt, timezone);
  if (startsAt === null || endsAt === null || !/^\d{1,3}$/u.test(fields.breakMinutes)) return null;
  return { startsAt, endsAt, breakMinutes: Number(fields.breakMinutes) };
}
