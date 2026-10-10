import type { HttpClient } from "../../../../core/data/http/http-client";
import { employeeDto, employeePageDto } from "../models/employee-dto";
import type { EmployeeSearchDto } from "../models/employee-search-dto";
import { employmentHistoryPageDto } from "../models/employment-revision-dto";
import type { EmployeeDataSource } from "./employee-data-source";

export class HttpEmployeeDataSource implements EmployeeDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(companyId: string, search: EmployeeSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ asOf: search.asOf, query: search.query, limit: "50" });
    if (search.after !== null) query.set("after", search.after);
    return employeePageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees?${query}` },
        signal,
      ),
    );
  }
  async get(companyId: string, id: string, asOf: string, signal: AbortSignal) {
    const query = new URLSearchParams({ asOf });
    return employeeDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${id}?${query}` },
        signal,
      ),
    );
  }
  async history(companyId: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "50" });
    if (after !== null) query.set("after", after);
    return employmentHistoryPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${companyId}/employees/${id}/history?${query}` },
        signal,
      ),
    );
  }
}
