import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type TaxOpeningChangeDto,
  type TaxOpeningVerificationDto,
  taxOpeningDto,
  taxOpeningEmployeesDto,
  taxOpeningReceiptDto,
  taxOpeningReviewDto,
} from "../models/tax-opening-dto";
import type { TaxOpeningDataSource } from "./tax-opening-data-source";
export class HttpTaxOpeningDataSource implements TaxOpeningDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(
    company: string,
    year: string,
    query: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ year, query, limit: "50" });
    if (status !== null) parameters.set("status", status);
    if (after !== null) parameters.set("after", after);
    return taxOpeningEmployeesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/tax-openings/employees?${parameters}` },
        signal,
      ),
    );
  }
  async review(
    company: string,
    employee: string,
    year: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    const parameters = new URLSearchParams({ historyLimit: "20" });
    if (after !== null) parameters.set("historyAfter", after);
    return taxOpeningReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/tax-openings/${year}/review?${parameters}`,
        },
        signal,
      ),
    );
  }
  async revision(
    company: string,
    employee: string,
    year: string,
    revision: number,
    signal: AbortSignal,
  ) {
    return taxOpeningDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/tax-openings/${year}?revision=${revision}`,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    employee: string,
    year: string,
    operation: string,
    input: TaxOpeningChangeDto,
    signal: AbortSignal,
  ) {
    return taxOpeningReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/tax-openings/${year}`,
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
    year: string,
    operation: string,
    input: TaxOpeningVerificationDto,
    signal: AbortSignal,
  ) {
    return taxOpeningReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/tax-openings/${year}/verify`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
