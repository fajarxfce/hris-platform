import { canReadCompanyPayroll } from "../../../payroll/domain/policies/payroll-payslip-policy";
import { validPayrollMonth } from "../../../payroll/domain/policies/payroll-period-policy";

export function canReadPayrollCostReport(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && canReadCompanyPayroll(permissions);
}
export function isPayrollCostReportRange(from: string, until: string): boolean {
  if (!validPayrollMonth(from) || !validPayrollMonth(until) || from > until) return false;
  const length =
    (Number(until.slice(0, 4)) - Number(from.slice(0, 4))) * 12 +
    Number(until.slice(5)) -
    Number(from.slice(5));
  return length <= 11;
}
export const payrollCostReportRangeParameters = Object.freeze({
  maximumMonths: "12",
  minimumYear: "2024",
  maximumYear: "2100",
});
