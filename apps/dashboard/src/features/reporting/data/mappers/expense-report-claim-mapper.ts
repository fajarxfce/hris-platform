import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  ExpenseReportClaimPage,
  ExpenseReportQuery,
} from "../../domain/entities/expense-report-claim";
import {
  expenseReportCursorPrefix,
  isExpenseReportRange,
} from "../../domain/policies/expense-report-policy";
import type { ExpenseReportClaimPageDto } from "../models/expense-report-dto";
import { expenseReportAmountMinor, maximumReportClaimMinor } from "./expense-report-mapper";

export function toExpenseReportClaimPage(
  dto: ExpenseReportClaimPageDto,
  company: CompanyId,
  query: ExpenseReportQuery,
): ExpenseReportClaimPage {
  if (
    !isExpenseReportRange(query.from, query.until) ||
    dto.companyId !== company ||
    dto.from !== query.from ||
    dto.until !== query.until ||
    dto.status !== query.status
  )
    throw new InvalidHttpResponseError();
  const prefix = expenseReportCursorPrefix(company, query);
  let previous = query.after?.slice(prefix.length).split("|") ?? null;
  const ids = new Set<string>();
  const submissions = new Set<string>();
  const items = dto.items.map((row) => {
    const amount = expenseReportAmountMinor(row.amount);
    const whole = expenseReportAmountMinor(row.submissionAmount);
    if (
      amount < BigInt(row.lines) ||
      amount > whole ||
      whole > maximumReportClaimMinor ||
      (query.status !== null && row.status !== query.status) ||
      ids.has(row.id) ||
      submissions.has(row.submissionId) ||
      ![row.employeeNumber, row.employeeName, row.title].every((value) => value.trim().length > 0)
    )
      throw new InvalidHttpResponseError();
    if (previous) {
      const [at = "", id = ""] = previous;
      const order = Temporal.Instant.compare(row.submittedAt, at);
      if (order > 0 || (order === 0 && row.id >= id)) throw new InvalidHttpResponseError();
    }
    ids.add(row.id);
    submissions.add(row.submissionId);
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
    currency: dto.currency,
    items: Object.freeze(items),
    nextCursor: dto.nextCursor,
  });
}
