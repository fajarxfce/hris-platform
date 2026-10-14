import type { HttpClient } from "../../../../core/data/http/http-client";
import type { CompensationChangeDto } from "../models/compensation-change-dto";
import {
  compensationEmployeesDto,
  compensationReceiptDto,
  compensationReviewDto,
} from "../models/compensation-dto";
import type { CompensationDataSource } from "./compensation-data-source";
export class HttpCompensationDataSource implements CompensationDataSource {
  constructor(private readonly http: HttpClient) {}
  async employees(company: string, query: string, after: string | null, signal: AbortSignal) {
    const parameters = new URLSearchParams({ query, limit: "20" });
    if (after !== null) parameters.set("after", after);
    return compensationEmployeesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/payroll/compensation-employees?${parameters}` },
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
    const parameters = new URLSearchParams({ asOf: month, historyLimit: "20" });
    if (after !== null) parameters.set("historyAfter", after);
    return compensationReviewDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/compensation/review?${parameters}`,
        },
        signal,
      ),
    );
  }
  async save(
    company: string,
    employee: string,
    operation: string,
    input: CompensationChangeDto,
    signal: AbortSignal,
  ) {
    return compensationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/payroll/employees/${employee}/compensation`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
