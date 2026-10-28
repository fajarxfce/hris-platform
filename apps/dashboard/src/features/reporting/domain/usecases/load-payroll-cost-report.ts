import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canReadPayrollCostReport,
  isPayrollCostReportRange,
  payrollCostReportRangeParameters,
} from "../policies/payroll-cost-report-policy";
import type { PayrollCostReportRepository } from "../repositories/payroll-cost-report-repository";

export class LoadPayrollCostReport {
  constructor(private readonly reports: PayrollCostReportRepository) {}
  execute(access: CompanyAccess, from: string, until: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollCostReport(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isPayrollCostReportRange(from, until))
      return Promise.resolve({
        ok: false as const,
        failure: {
          code: "invalid_report_month_range",
          fields: {},
          parameters: payrollCostReportRangeParameters,
        },
      });
    return this.reports.load(access.companyId, from, until, signal);
  }
}
