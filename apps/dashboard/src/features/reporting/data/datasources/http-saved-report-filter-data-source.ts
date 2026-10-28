import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  reportFilterReceiptDto,
  type SaveReportFilterDto,
  savedReportFilterDto,
  savedReportFiltersDto,
} from "../models/saved-report-filter-dto";
import type { SavedReportFilterDataSource } from "./saved-report-filter-data-source";

const path = (company: string) =>
  `/api/v1/companies/${encodeURIComponent(company)}/reports/saved-filters`;
export class HttpSavedReportFilterDataSource implements SavedReportFilterDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(company: string, signal: AbortSignal) {
    return savedReportFiltersDto.parse(await this.http.request({ path: path(company) }, signal));
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return savedReportFilterDto.parse(
      await this.http.request({ path: `${path(company)}/${encodeURIComponent(id)}` }, signal),
    );
  }
  async save(
    company: string,
    id: string,
    operation: string,
    input: SaveReportFilterDto,
    signal: AbortSignal,
  ) {
    return reportFilterReceiptDto.parse(
      await this.http.request(
        {
          path: `${path(company)}/${encodeURIComponent(id)}`,
          method: "PUT",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async delete(
    company: string,
    id: string,
    operation: string,
    expectedVersion: number,
    signal: AbortSignal,
  ) {
    return reportFilterReceiptDto.parse(
      await this.http.request(
        {
          path: `${path(company)}/${encodeURIComponent(id)}/delete`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion },
        },
        signal,
      ),
    );
  }
}
