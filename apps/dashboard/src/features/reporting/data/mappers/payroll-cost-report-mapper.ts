import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  PayrollCostReport,
  PayrollCostReportCounts,
} from "../../domain/entities/payroll-cost-report";
import { isPayrollCostReportRange } from "../../domain/policies/payroll-cost-report-policy";
import type { PayrollCostReportDto } from "../models/payroll-cost-report-dto";

const moneyKeys = [
  "employerCost",
  "taxableGross",
  "employeeDeductions",
  "withheld",
  "takeHome",
] as const;
// DTO validation guarantees a signed decimal with exactly two fractional digits.
const cents = (value: string): bigint => BigInt(value.replace(".", ""));

function counts(value: PayrollCostReportCounts): PayrollCostReportCounts {
  const a = value.assessments,
    e = value.employments;
  const cost = cents(value.employerCost),
    gross = cents(value.taxableGross),
    deductions = cents(value.employeeDeductions),
    tax = cents(value.withheld),
    net = cents(value.takeHome);
  const maximum = BigInt(a) * 5_000_000_000_000n;
  const refund = BigInt(a) * 120_000_000_000_000n;
  const basis = net + deductions + tax;
  if (
    e > a ||
    (a === 0) !== (e === 0) ||
    value.revisedAssessments > a ||
    cost > maximum ||
    gross > cost ||
    deductions > cost ||
    tax < -refund ||
    tax > cost ||
    net > cost + refund ||
    basis < 0n ||
    basis > cost
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...value });
}

export function toPayrollCostReport(
  dto: PayrollCostReportDto,
  company: CompanyId,
  from: string,
  until: string,
): PayrollCostReport {
  if (
    !isPayrollCostReportRange(from, until) ||
    dto.companyId !== company ||
    dto.from !== from ||
    dto.until !== until
  )
    throw new InvalidHttpResponseError();
  const year = Number(from.slice(0, 4)),
    firstMonth = Number(from.slice(5)) - 1;
  const length = (Number(until.slice(0, 4)) - year) * 12 + Number(until.slice(5)) - firstMonth;
  if (dto.months.length !== length) throw new InvalidHttpResponseError();
  const totals = counts(dto.totals);
  const months = dto.months.map((row, index) => {
    const month = new Date(Date.UTC(year, firstMonth + index, 1)).toISOString().slice(0, 7);
    if (row.month !== month || row.counts.assessments !== row.counts.employments)
      throw new InvalidHttpResponseError();
    return Object.freeze({ month, counts: counts(row.counts) });
  });
  for (const key of ["assessments", "revisedAssessments"] as const)
    if (months.reduce((sum, row) => sum + BigInt(row.counts[key]), 0n) !== BigInt(totals[key]))
      throw new InvalidHttpResponseError();
  if (
    totals.employments < Math.max(...months.map((row) => row.counts.employments)) ||
    BigInt(totals.employments) >
      months.reduce((sum, row) => sum + BigInt(row.counts.employments), 0n)
  )
    throw new InvalidHttpResponseError();
  for (const key of moneyKeys)
    if (months.reduce((sum, row) => sum + cents(row.counts[key]), 0n) !== cents(totals[key]))
      throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    from,
    until,
    evaluatedAt: dto.evaluatedAt,
    definitionVersion: dto.definitionVersion,
    currency: dto.currency,
    totals,
    months: Object.freeze(months),
  });
}
