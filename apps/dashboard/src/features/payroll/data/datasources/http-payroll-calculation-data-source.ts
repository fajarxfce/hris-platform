import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollCalculationRecoveryDto,
  type PayrollCalculationStartDto,
  payrollCalculationReceiptDto,
  payrollCalculationReviewDto,
  payrollRunDetailsDto,
  payrollRunEmployeeDto,
  payrollRunsDto,
} from "../models/payroll-calculation-dto";
import type { PayrollCalculationDataSource } from "./payroll-calculation-data-source";
export class HttpPayrollCalculationDataSource implements PayrollCalculationDataSource {
  constructor(private readonly http: HttpClient) {}
  async review(company: string, period: string, signal: AbortSignal) {
    return payrollCalculationReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/periods/${period}/calculation-review` },
        signal,
      ),
    );
  }
  async runs(company: string, period: string, signal: AbortSignal) {
    return payrollRunsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/periods/${period}/runs?limit=20` },
        signal,
      ),
    );
  }
  async run(company: string, id: string, after: string | null, limit: number, signal: AbortSignal) {
    const params = new URLSearchParams({ limit: String(limit) });
    if (after !== null) params.set("after", after);
    return payrollRunDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${id}?${params}` },
        signal,
      ),
    );
  }
  async employee(company: string, id: string, employee: string, signal: AbortSignal) {
    return payrollRunEmployeeDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${id}/employees/${employee}` },
        signal,
      ),
    );
  }
  async start(
    company: string,
    period: string,
    operation: string,
    input: PayrollCalculationStartDto,
    signal: AbortSignal,
  ) {
    return payrollCalculationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/periods/${period}/runs`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async resume(
    company: string,
    id: string,
    operation: string,
    input: PayrollCalculationRecoveryDto,
    signal: AbortSignal,
  ) {
    return payrollCalculationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/runs/${id}/resume`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion: input.expectedVersion, reason: input.reason },
        },
        signal,
      ),
    );
  }
  async abandon(
    company: string,
    id: string,
    operation: string,
    input: PayrollCalculationRecoveryDto,
    signal: AbortSignal,
  ) {
    return payrollCalculationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/runs/${id}/abandon`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
