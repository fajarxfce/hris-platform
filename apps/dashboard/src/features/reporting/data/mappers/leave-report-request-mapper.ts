import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  LeaveReportQuery,
  LeaveReportRequestPage,
} from "../../domain/entities/leave-report-request";
import {
  isLeaveReportRange,
  leaveReportCursorPrefix,
} from "../../domain/policies/leave-report-policy";
import type { LeaveReportRequestPageDto } from "../models/leave-report-dto";
import { reportHalfDays } from "./leave-report-mapper";

export function toLeaveReportRequestPage(
  dto: LeaveReportRequestPageDto,
  company: CompanyId,
  query: LeaveReportQuery,
): LeaveReportRequestPage {
  if (
    !isLeaveReportRange(query.from, query.until) ||
    dto.companyId !== company ||
    dto.from !== query.from ||
    dto.until !== query.until ||
    dto.status !== query.status
  )
    throw new InvalidHttpResponseError();
  const prefix = leaveReportCursorPrefix(company, query);
  let previous = query.after?.slice(prefix.length).split("|") ?? null;
  const ids = new Set<string>();
  const items = dto.items.map((row) => {
    const units = reportHalfDays(row.days);
    const whole = reportHalfDays(row.requestDays);
    if (
      units < 1n ||
      units > whole ||
      whole > 732n ||
      (query.status !== null && row.status !== query.status) ||
      ids.has(row.id) ||
      ![row.employeeNumber, row.employeeName, row.typeCode, row.typeName].every(
        (value) => value.trim().length > 0,
      )
    )
      throw new InvalidHttpResponseError();
    if (previous) {
      const [at = "", id = ""] = previous;
      const order = Temporal.Instant.compare(row.submittedAt, at);
      if (order > 0 || (order === 0 && row.id >= id)) throw new InvalidHttpResponseError();
    }
    ids.add(row.id);
    previous = [row.submittedAt, row.id];
    return Object.freeze({ ...row });
  });
  const last = items.at(-1);
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 ||
      !last ||
      dto.nextCursor !== `${prefix}${last.submittedAt}|${last.id}` ||
      dto.nextCursor === query.after)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    from: query.from,
    until: query.until,
    status: query.status,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    items: Object.freeze(items),
    nextCursor: dto.nextCursor,
  });
}
