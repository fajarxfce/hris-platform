import type { PayrollCostReportDto } from "../models/payroll-cost-report-dto";

export interface PayrollCostReportDataSource {
  load(
    company: string,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<PayrollCostReportDto>;
}
