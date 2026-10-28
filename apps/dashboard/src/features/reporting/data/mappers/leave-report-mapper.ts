import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { leaveStatuses } from "../../../leave/domain/entities/leave-request";
import type { LeaveReport, LeaveReportCounts } from "../../domain/entities/leave-report";
import { isLeaveReportRange } from "../../domain/policies/leave-report-policy";
import type { LeaveReportCountsDto, LeaveReportDto } from "../models/leave-report-dto";

export function reportHalfDays(days: string): bigint {
  const [whole = "0", half] = days.split(".");
  const result = BigInt(whole) * 2n + (half === "5" ? 1n : 0n);
  if (result > 9223372036854775807n) throw new InvalidHttpResponseError();
  return result;
}

function counts(dto: LeaveReportCountsDto): LeaveReportCounts {
  const units = reportHalfDays(dto.days);
  const requests = BigInt(dto.requests);
  if (
    dto.employments > dto.requests ||
    (dto.requests === 0) !== (dto.employments === 0) ||
    units < requests ||
    units > requests * 732n
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}

export function toLeaveReport(
  dto: LeaveReportDto,
  company: CompanyId,
  from: string,
  until: string,
): LeaveReport {
  if (
    !isLeaveReportRange(from, until) ||
    dto.companyId !== company ||
    dto.from !== from ||
    dto.until !== until
  )
    throw new InvalidHttpResponseError();
  const totals = counts(dto.totals);
  const statuses = dto.statuses.map((row, i) => {
    if (row.status !== leaveStatuses[i]) throw new InvalidHttpResponseError();
    return Object.freeze({ status: row.status, counts: counts(row.counts) });
  });
  const requests = statuses.reduce((sum, row) => sum + BigInt(row.counts.requests), 0n);
  const employments = statuses.reduce((sum, row) => sum + BigInt(row.counts.employments), 0n);
  const units = statuses.reduce((sum, row) => sum + reportHalfDays(row.counts.days), 0n);
  if (
    requests !== BigInt(totals.requests) ||
    units !== reportHalfDays(totals.days) ||
    BigInt(totals.employments) > employments ||
    totals.employments < Math.max(...statuses.map((row) => row.counts.employments))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    from,
    until,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    totals,
    statuses: Object.freeze(statuses),
  });
}
