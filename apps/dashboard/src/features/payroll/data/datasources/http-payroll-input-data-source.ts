import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollInputChangeDto,
  type PayrollInputVerificationDto,
  payrollInputDto,
  payrollInputEmployeesDto,
  payrollInputReceiptDto,
  payrollInputReviewDto,
} from "../models/payroll-input-dto";
import type { PayrollInputDataSource } from "./payroll-input-data-source";
export class HttpPayrollInputDataSource implements PayrollInputDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(
    company: string,
    month: string,
    query: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ month, query, limit: "50" });
    if (status !== null) parameters.set("status", status);
    if (after !== null) parameters.set("after", after);
    return payrollInputEmployeesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/inputs/employees?${parameters}` },
        signal,
      ),
    );
  }
  async review(
    company: string,
    employee: string,
    month: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ historyLimit: "20" });
    if (after !== null) parameters.set("historyAfter", after);
    return payrollInputReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/inputs/${month}/review?${parameters}`,
        },
        signal,
      ),
    );
  }
  async revision(
    company: string,
    employee: string,
    month: string,
    revision: number,
    signal: AbortSignal,
  ) {
    return payrollInputDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/inputs/${month}?revision=${revision}`,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    employee: string,
    month: string,
    operation: string,
    input: PayrollInputChangeDto,
    signal: AbortSignal,
  ) {
    return payrollInputReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/inputs/${month}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async verify(
    company: string,
    employee: string,
    month: string,
    operation: string,
    input: PayrollInputVerificationDto,
    signal: AbortSignal,
  ) {
    return payrollInputReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/inputs/${month}/verify`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
