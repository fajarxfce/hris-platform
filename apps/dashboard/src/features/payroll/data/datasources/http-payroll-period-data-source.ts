import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollPeriodCreationDto,
  payrollPeriodDetailsDto,
  payrollPeriodEmployeesDto,
  payrollPeriodReceiptDto,
  payrollPeriodsDto,
} from "../models/payroll-period-dto";
import type { PayrollPeriodDataSource } from "./payroll-period-data-source";
export class HttpPayrollPeriodDataSource implements PayrollPeriodDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ from, until, limit: "20" });
    if (status !== null) parameters.set("status", status);
    if (after !== null) parameters.set("after", after);
    return payrollPeriodsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/periods?${parameters}` },
        signal,
      ),
    );
  }
  async details(
    company: string,
    id: string,
    memberAfter: string | null,
    historyAfter: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ limit: "50", historyLimit: "20" });
    if (memberAfter !== null) parameters.set("after", memberAfter);
    if (historyAfter !== null) parameters.set("historyAfter", historyAfter);
    return payrollPeriodDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/periods/${id}?${parameters}` },
        signal,
      ),
    );
  }
  async employees(company: string, query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "50" });
    if (after !== null) parameters.set("after", after);
    return payrollPeriodEmployeesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/periods/employees?${parameters}` },
        signal,
      ),
    );
  }
  async create(
    company: string,
    operation: string,
    input: PayrollPeriodCreationDto,
    signal: AbortSignal,
  ) {
    return payrollPeriodReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/periods`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async cancel(
    company: string,
    id: string,
    operation: string,
    version: number,
    reason: string,
    signal: AbortSignal,
  ) {
    return payrollPeriodReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/periods/${id}/cancel`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion: version, reason },
        },
        signal,
      ),
    );
  }
}
