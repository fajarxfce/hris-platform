import type { HttpClient } from "../../../../core/data/http/http-client";
import { payrollCostReportDto } from "../models/payroll-cost-report-dto";
import type { PayrollCostReportDataSource } from "./payroll-cost-report-data-source";

export class HttpPayrollCostReportDataSource implements PayrollCostReportDataSource {
  constructor(private readonly http: HttpClient) {}
  async load(company: string, from: string, until: string, signal: AbortSignal) {
    const query = new URLSearchParams({ from, until });
    return payrollCostReportDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${encodeURIComponent(company)}/reports/payroll-costs?${query}`,
        },
        signal,
      ),
    );
  }
}
