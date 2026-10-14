import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type PayrollFinalizationStartDto,
  type PayrollPublicationSearchDto,
  payrollFinalizationDetailsDto,
  payrollFinalizationPreparationDto,
  payrollFinalizationReceiptDto,
  payrollFinalizationsDto,
  payrollPublicationPageDto,
} from "../models/payroll-finalization-dto";
import type { PayrollFinalizationDataSource } from "./payroll-finalization-data-source";
export class HttpPayrollFinalizationDataSource implements PayrollFinalizationDataSource {
  constructor(private readonly http: HttpClient) {}
  async runs(company: string, search: PayrollPublicationSearchDto, signal: AbortSignal) {
    const parameters = new URLSearchParams({ from: search.from, until: search.until, limit: "50" });
    if (search.status !== null) parameters.set("status", search.status);
    if (search.after !== null) parameters.set("after", search.after);
    return payrollPublicationPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/publication-runs?${parameters}` },
        signal,
      ),
    );
  }
  async prepare(company: string, run: string, signal: AbortSignal) {
    return payrollFinalizationPreparationDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${run}/finalization-preparation` },
        signal,
      ),
    );
  }
  async list(company: string, run: string, signal: AbortSignal) {
    return payrollFinalizationsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/runs/${run}/finalizations?limit=8` },
        signal,
      ),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return payrollFinalizationDetailsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/finalizations/${id}` },
        signal,
      ),
    );
  }
  async start(
    company: string,
    run: string,
    operation: string,
    input: PayrollFinalizationStartDto,
    signal: AbortSignal,
  ) {
    return payrollFinalizationReceiptDto.parse(
      await this.http.request(
        {
          method: "POST",
          path: `/api/v1/companies/${company}/payroll/runs/${run}/finalizations`,
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
