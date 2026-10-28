import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeStatus } from "../../../workforce/domain/entities/overtime-request";
import type { OvertimeReport, OvertimeReportCounts } from "../../domain/entities/overtime-report";
import { isOvertimeReportRange } from "../../domain/policies/overtime-report-policy";
import type { OvertimeReportDto } from "../models/overtime-report-dto";

function counts(value: OvertimeReportCounts, status: OvertimeStatus | null): OvertimeReportCounts {
  const {
    requests: r,
    employments: e,
    actualRequests: a,
    requestedMinutes: planned,
    actualMinutes: actual,
    approvedMinutes: approved,
  } = value;
  if (
    e > r ||
    (r === 0) !== (e === 0) ||
    a > r ||
    planned < r ||
    BigInt(planned) > BigInt(r) * 720n ||
    actual < a ||
    BigInt(actual) > BigInt(a) * 720n ||
    actual > planned ||
    approved > actual ||
    (status === "PLANNED" && a !== 0) ||
    (["PENDING", "APPROVED", "REJECTED"].includes(status ?? "") && a !== r) ||
    (status === "APPROVED" ? approved !== actual : status !== null && approved !== 0)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...value });
}

export function toOvertimeReport(
  dto: OvertimeReportDto,
  company: CompanyId,
  from: string,
  until: string,
  status: OvertimeStatus | null,
): OvertimeReport {
  if (
    !isOvertimeReportRange(from, until) ||
    dto.companyId !== company ||
    dto.from !== from ||
    dto.until !== until ||
    (dto.status ?? null) !== status
  )
    throw new InvalidHttpResponseError();
  const first = new Date(`${from.slice(0, 7)}-01T00:00:00Z`);
  const length =
    (Number(until.slice(0, 4)) - first.getUTCFullYear()) * 12 +
    Number(until.slice(5, 7)) -
    first.getUTCMonth();
  if (dto.months.length !== length) throw new InvalidHttpResponseError();
  const totals = counts(dto.totals, status);
  const months = dto.months.map((row, index) => {
    const date = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + index, 1));
    const month = date.toISOString().slice(0, 7);
    const end = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0))
      .toISOString()
      .slice(0, 10);
    if (
      row.month !== month ||
      row.from !== (index === 0 ? from : `${month}-01`) ||
      row.until !== (index === length - 1 ? until : end)
    )
      throw new InvalidHttpResponseError();
    return Object.freeze({
      month,
      from: row.from,
      until: row.until,
      counts: counts(row.counts, status),
    });
  });
  for (const key of [
    "requests",
    "actualRequests",
    "requestedMinutes",
    "actualMinutes",
    "approvedMinutes",
  ] as const) {
    if (months.reduce((sum, row) => sum + BigInt(row.counts[key]), 0n) !== BigInt(totals[key]))
      throw new InvalidHttpResponseError();
  }
  if (
    totals.employments < Math.max(...months.map((row) => row.counts.employments)) ||
    BigInt(totals.employments) >
      months.reduce((sum, row) => sum + BigInt(row.counts.employments), 0n)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    from,
    until,
    status,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    totals,
    months: Object.freeze(months),
  });
}
