import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type EmployeeImportApplicationDto,
  type EmployeeImportChangeDto,
  employeeImportReceiptDto,
} from "../models/employee-import-change-dto";
import {
  employeeImportAttemptsDto,
  employeeImportPageDto,
  employeeImportRowsDto,
  employeeImportSummaryDto,
} from "../models/employee-import-dto";
import {
  type EmployeeImportStartDto,
  employeeImportTemplateDto,
  employeeImportTemplateMaximumBytes,
} from "../models/employee-import-start-dto";
import type { EmployeeImportDataSource } from "./employee-import-data-source";

export class HttpEmployeeImportDataSource implements EmployeeImportDataSource {
  async start(
    company: string,
    operation: string,
    input: EmployeeImportStartDto,
    signal: AbortSignal,
  ) {
    return employeeImportReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employee-imports`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
  async template(company: string, signal: AbortSignal) {
    return employeeImportTemplateDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employee-imports/template`,
          response: {
            type: "text",
            mediaType: "text/csv",
            maximumBytes: employeeImportTemplateMaximumBytes,
          },
        },
        signal,
      ),
    );
  }
  async apply(
    company: string,
    id: string,
    operation: string,
    change: EmployeeImportApplicationDto,
    signal: AbortSignal,
  ) {
    return employeeImportReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employee-imports/${id}/apply`,
          method: "POST",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async resume(
    company: string,
    id: string,
    operation: string,
    change: EmployeeImportChangeDto,
    signal: AbortSignal,
  ) {
    return employeeImportReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employee-imports/${id}/resume`,
          method: "POST",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  async cancel(
    company: string,
    id: string,
    operation: string,
    change: EmployeeImportChangeDto,
    signal: AbortSignal,
  ) {
    return employeeImportReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/employee-imports/${id}/cancel`,
          method: "POST",
          operationId: operation,
          body: change,
        },
        signal,
      ),
    );
  }
  constructor(private readonly http: HttpClient) {}
  async list(company: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "10" });
    if (after !== null) query.set("after", after);
    return employeeImportPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employee-imports?${query}` },
        signal,
      ),
    );
  }
  async summary(company: string, id: string, signal: AbortSignal) {
    return employeeImportSummaryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employee-imports/${id}` },
        signal,
      ),
    );
  }
  async rows(company: string, id: string, after: number | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "25" });
    if (after !== null) query.set("after", String(after));
    return employeeImportRowsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employee-imports/${id}/rows?${query}` },
        signal,
      ),
    );
  }
  async attempts(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "10" });
    if (after !== null) query.set("after", after);
    return employeeImportAttemptsDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/employee-imports/${id}/attempts?${query}` },
        signal,
      ),
    );
  }
}
