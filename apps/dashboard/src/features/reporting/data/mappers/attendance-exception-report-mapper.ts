import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceExceptionReport } from "../../domain/entities/attendance-exception-report";
import { isAttendanceReportRange } from "../../domain/policies/attendance-report-policy";
import type { AttendanceExceptionReportDto } from "../models/attendance-exception-report-dto";
import { toAttendanceExceptionCounts } from "./attendance-exception-counts-mapper";

export function toAttendanceExceptionReport(
  dto: AttendanceExceptionReportDto,
  company: CompanyId,
  from: string,
  until: string,
): AttendanceExceptionReport {
  if (
    !isAttendanceReportRange(from, until) ||
    dto.companyId !== company ||
    dto.from !== from ||
    dto.until !== until
  )
    throw new InvalidHttpResponseError();
  const start = Date.parse(`${from}T00:00:00Z`);
  const length = (Date.parse(`${until}T00:00:00Z`) - start) / 86_400_000 + 1;
  if (dto.days.length !== length) throw new InvalidHttpResponseError();
  const totals = toAttendanceExceptionCounts(dto.totals);
  const days = dto.days.map((day, index) => {
    if (day.date !== new Date(start + index * 86_400_000).toISOString().slice(0, 10))
      throw new InvalidHttpResponseError();
    const counts = toAttendanceExceptionCounts(day.counts);
    if (counts.affectedEmployments !== counts.affectedDays) throw new InvalidHttpResponseError();
    return Object.freeze({ date: day.date, counts });
  });
  for (const metric of [
    "observedDays",
    "affectedDays",
    "pendingDays",
    "incompleteDays",
    "pendingEvents",
  ] as const) {
    const sum = days.reduce((value, day) => value + day.counts[metric], 0);
    if (!Number.isSafeInteger(sum) || sum !== totals[metric]) throw new InvalidHttpResponseError();
  }
  if (totals.affectedEmployments < Math.max(...days.map((day) => day.counts.affectedEmployments)))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    from,
    until,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    totals,
    days: Object.freeze(days),
  });
}
