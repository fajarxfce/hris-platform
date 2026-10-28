import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  AttendanceClosingCounts,
  AttendanceClosingEmployeePage,
  AttendanceClosingEmployeeQuery,
  AttendanceClosingFacts,
  AttendanceClosingReference,
  AttendanceClosingReport,
} from "../../domain/entities/attendance-closing-report";
import {
  isAttendanceClosingEmployeePage,
  isAttendanceClosingReportRange,
} from "../../domain/policies/attendance-closing-report-policy";
import type {
  AttendanceClosingEmployeePageDto,
  AttendanceClosingReportDto,
} from "../models/attendance-closing-report-dto";
export const closingFactKeys = [
  "workedDays",
  "absenceDays",
  "unrecordedDays",
  "offDays",
  "unassignedDays",
  "acceptedMinutes",
] as const;
const additive = [
  "closedMonths",
  "employmentMonths",
  "adjustedEmploymentMonths",
  "days",
  "scheduledDays",
  "scheduledMinutes",
] as const;
function check(ok: boolean): asserts ok {
  if (!ok) throw new InvalidHttpResponseError();
}
function facts(v: AttendanceClosingFacts, days: number): AttendanceClosingFacts {
  check(
    v.workedDays + v.absenceDays + v.unrecordedDays + v.offDays + v.unassignedDays === days &&
      v.acceptedMinutes <= v.workedDays * 1440,
  );
  return Object.freeze({ ...v });
}
function counts(v: AttendanceClosingCounts): AttendanceClosingCounts {
  check(
    v.closedMonths <= 12 &&
      v.employmentMonths <= v.closedMonths * 5000 &&
      v.employments <= v.employmentMonths &&
      (v.employments === 0) === (v.employmentMonths === 0),
  );
  check(
    v.adjustedEmploymentMonths <= v.employmentMonths &&
      v.days >= v.employmentMonths * 28 &&
      v.days <= v.employmentMonths * 31 &&
      v.scheduledDays <= v.days &&
      v.scheduledMinutes >= v.scheduledDays &&
      v.scheduledMinutes <= v.scheduledDays * 3600,
  );
  const original = facts(v.original, v.days),
    current = facts(v.current, v.days);
  check(
    current.offDays <= original.offDays &&
      current.unrecordedDays <= original.unrecordedDays &&
      current.unassignedDays <= original.unassignedDays,
  );
  if (v.adjustedEmploymentMonths === 0)
    check(closingFactKeys.every((key) => original[key] === current[key]));
  return Object.freeze({ ...v, original, current });
}
function reference(v: AttendanceClosingReference): AttendanceClosingReference {
  new Intl.DateTimeFormat("en", { timeZone: v.timezone });
  return Object.freeze({ ...v });
}
export function toAttendanceClosingReport(
  dto: AttendanceClosingReportDto,
  company: CompanyId,
  from: string,
  until: string,
): AttendanceClosingReport {
  check(
    isAttendanceClosingReportRange(from, until) &&
      dto.companyId === company &&
      dto.from === from &&
      dto.until === until,
  );
  const start = Temporal.PlainYearMonth.from(from),
    end = Temporal.PlainYearMonth.from(until);
  check(dto.months.length === start.until(end).months + start.until(end).years * 12 + 1);
  const totals = counts(dto.totals);
  const months = dto.months.map((row, index) => {
    const month = start.add({ months: index });
    check(row.month === month.toString());
    const value = counts(row.counts),
      closing = row.closing ? reference(row.closing) : null;
    check(
      value.closedMonths === (closing ? 1 : 0) &&
        value.employments === value.employmentMonths &&
        value.days === value.employments * month.daysInMonth,
    );
    return Object.freeze({ month: row.month, closing, counts: value });
  });
  const refs = months.flatMap((row) => (row.closing ? [row.closing] : []));
  check(
    new Set(refs.map((row) => row.jobId)).size === refs.length &&
      new Set(refs.map((row) => row.periodId)).size === refs.length,
  );
  for (const key of additive)
    check(months.reduce((sum, row) => sum + row.counts[key], 0) === totals[key]);
  check(
    totals.employments >= Math.max(...months.map((row) => row.counts.employments)) &&
      totals.employments <= months.reduce((sum, row) => sum + row.counts.employments, 0),
  );
  for (const source of ["original", "current"] as const)
    for (const key of closingFactKeys)
      check(months.reduce((sum, row) => sum + row.counts[source][key], 0) === totals[source][key]);
  return Object.freeze({ ...dto, companyId: company, totals, months: Object.freeze(months) });
}
export function toAttendanceClosingEmployeePage(
  dto: AttendanceClosingEmployeePageDto,
  company: CompanyId,
  query: AttendanceClosingEmployeeQuery,
): AttendanceClosingEmployeePage {
  check(
    isAttendanceClosingReportRange(query.month, query.month) &&
      isAttendanceClosingEmployeePage(company, query) &&
      dto.companyId === company &&
      dto.month === query.month,
  );
  const days = Temporal.PlainYearMonth.from(query.month).daysInMonth;
  let previous = query.after?.split("|").at(-1) ?? "";
  const items = dto.items.map((row) => {
    check(row.employeeId > previous);
    previous = row.employeeId;
    const value = counts(row.counts),
      closing = reference(row.closing);
    check(
      value.closedMonths === 1 &&
        value.employmentMonths === 1 &&
        value.employments === 1 &&
        value.days === days &&
        value.adjustedEmploymentMonths === (row.adjustment ? 1 : 0),
    );
    if (row.adjustment)
      check(Temporal.Instant.compare(row.adjustment.recordedAt, closing.closedAt) >= 0);
    return Object.freeze({
      ...row,
      closing,
      counts: value,
      adjustment: row.adjustment ? Object.freeze({ ...row.adjustment }) : null,
    });
  });
  const first = items[0]?.closing;
  check(
    items.every(
      (row) =>
        row.closing.periodId === first?.periodId &&
        row.closing.jobId === first.jobId &&
        row.closing.version === first.version &&
        row.closing.closedAt === first.closedAt &&
        row.closing.timezone === first.timezone,
    ),
  );
  if (dto.nextCursor !== null)
    check(
      items.length === 50 &&
        isAttendanceClosingEmployeePage(company, { ...query, after: dto.nextCursor }) &&
        dto.nextCursor ===
          `attendance-closings.v1|${company}|${query.month}|${items.at(-1)?.employeeId}` &&
        dto.nextCursor !== query.after,
    );
  return Object.freeze({ ...dto, companyId: company, items: Object.freeze(items) });
}
