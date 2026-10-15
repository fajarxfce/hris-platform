import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollAmendmentCommandDto,
  type PayrollAmendmentSaveDto,
  payrollAmendmentReceiptDto,
} from "../models/payroll-amendment-command-dto";
import {
  type PayrollAmendmentSearchDto,
  payrollAmendmentCalculationDto,
  payrollAmendmentDetailsDto,
  payrollAmendmentDto,
  payrollAmendmentHistoryDto,
  payrollAmendmentPageDto,
} from "../models/payroll-amendment-dto";
import type { PayrollAmendmentDataSource } from "./payroll-amendment-data-source";
export class HttpPayrollAmendmentDataSource implements PayrollAmendmentDataSource {
  constructor(private readonly http: HttpClient) {}
  async calculate(
    company: string,
    id: string,
    operation: string,
    input: PayrollAmendmentCommandDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}/calculate`,
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
    input: PayrollAmendmentCommandDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}/cancel`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    input: PayrollAmendmentSaveDto,
    signal: AbortSignal,
  ) {
    return payrollAmendmentReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async list(company: string, search: PayrollAmendmentSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams({
      year: String(search.year),
      query: search.query,
      limit: "50",
    });
    if (search.employeeId !== null) parameters.set("employeeId", search.employeeId);
    if (search.status !== null) parameters.set("status", search.status);
    if (search.after !== null) parameters.set("after", search.after);
    return payrollAmendmentPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendments?${parameters}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return payrollAmendmentDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendments/${id}` },
        signal,
      ),
    );
  }
  async history(company: string, id: string, after: number, signal: AbortSignal) {
    return payrollAmendmentHistoryDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}/history?after=${after}&limit=25`,
        },
        signal,
      ),
    );
  }
  async revision(company: string, id: string, version: number, signal: AbortSignal) {
    return payrollAmendmentDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/amendments/${id}/revisions/${version}` },
        signal,
      ),
    );
  }
  async calculation(company: string, id: string, calculationId: string, signal: AbortSignal) {
    return payrollAmendmentCalculationDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/amendments/${id}/calculations/${calculationId}`,
        },
        signal,
      ),
    );
  }
}
