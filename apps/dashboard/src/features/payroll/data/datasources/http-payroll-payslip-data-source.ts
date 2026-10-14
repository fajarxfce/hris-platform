import { binaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollDocumentLanguageDto,
  type PayrollPayslipScopeDto,
  type PayrollPayslipSearchDto,
  payrollPaymentProgressDto,
  payrollPayslipDto,
  payrollPayslipPageDto,
} from "../models/payroll-payslip-dto";
import type { PayrollPayslipDataSource } from "./payroll-payslip-data-source";

export class HttpPayrollPayslipDataSource implements PayrollPayslipDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, search: PayrollPayslipSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams({
      from: search.from,
      until: search.until,
      query: search.query,
      scope: search.scope,
      limit: "50",
    });
    if (search.employeeId !== null) parameters.set("employeeId", search.employeeId);
    if (search.after !== null) parameters.set("after", search.after);
    return payrollPayslipPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/payslips?${parameters}` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, scope: PayrollPayslipScopeDto, signal: AbortSignal) {
    return payrollPayslipDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${id}?${new URLSearchParams({ scope })}`,
        },
        signal,
      ),
    );
  }
  async payments(company: string, id: string, scope: PayrollPayslipScopeDto, signal: AbortSignal) {
    return payrollPaymentProgressDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${id}/payments?${new URLSearchParams({ scope })}`,
        },
        signal,
      ),
    );
  }
  async pdf(
    company: string,
    id: string,
    scope: PayrollPayslipScopeDto,
    language: PayrollDocumentLanguageDto,
    signal: AbortSignal,
  ) {
    return binaryResponseDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/payslips/${id}/pdf?${new URLSearchParams({ scope, language })}`,
          response: { type: "binary", mediaType: "application/pdf", maximumBytes: 1024 * 1024 },
        },
        signal,
      ),
    );
  }
}
