import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { expenseClaimStatuses } from "../../../expenses/domain/entities/expense-claim";
import type { ExpenseReport, ExpenseReportCounts } from "../../domain/entities/expense-report";
import { isExpenseReportRange } from "../../domain/policies/expense-report-policy";
import type { ExpenseReportCountsDto, ExpenseReportDto } from "../models/expense-report-dto";

/** DTO parsing bounds the decimal string before exact conversion; report totals can exceed a single claim. */
export function expenseReportAmountMinor(amount: string): bigint {
  const [whole = "0", fraction = ""] = amount.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
export const maximumReportClaimMinor = 99_999_999_999_999n;

function counts(dto: ExpenseReportCountsDto): ExpenseReportCounts {
  const amount = expenseReportAmountMinor(dto.amount);
  const claims = BigInt(dto.claims),
    lines = BigInt(dto.lines);
  if (
    dto.employments > dto.claims ||
    (dto.claims === 0) !== (dto.employments === 0) ||
    lines < claims ||
    lines > claims * 20n ||
    amount < lines ||
    amount > claims * maximumReportClaimMinor
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto });
}
export function toExpenseReport(
  dto: ExpenseReportDto,
  company: CompanyId,
  from: string,
  until: string,
): ExpenseReport {
  if (
    !isExpenseReportRange(from, until) ||
    dto.companyId !== company ||
    dto.from !== from ||
    dto.until !== until
  )
    throw new InvalidHttpResponseError();
  const totals = counts(dto.totals);
  const statuses = dto.statuses.map((row, i) => {
    if (row.status !== expenseClaimStatuses[i]) throw new InvalidHttpResponseError();
    return Object.freeze({ status: row.status, counts: counts(row.counts) });
  });
  const claims = statuses.reduce((sum, row) => sum + BigInt(row.counts.claims), 0n);
  const lines = statuses.reduce((sum, row) => sum + BigInt(row.counts.lines), 0n);
  const employments = statuses.reduce((sum, row) => sum + BigInt(row.counts.employments), 0n);
  const amount = statuses.reduce(
    (sum, row) => sum + expenseReportAmountMinor(row.counts.amount),
    0n,
  );
  if (
    claims !== BigInt(totals.claims) ||
    lines !== BigInt(totals.lines) ||
    amount !== expenseReportAmountMinor(totals.amount) ||
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
    currency: dto.currency,
    totals,
    statuses: Object.freeze(statuses),
  });
}
