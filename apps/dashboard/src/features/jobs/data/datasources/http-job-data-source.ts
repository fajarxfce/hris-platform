import type { HttpClient } from "../../../../core/data/http/http-client";
import { jobDto, jobPageDto } from "../models/job-dto";
import type { JobSearchDto } from "../models/job-search-dto";
import type { JobDataSource } from "./job-data-source";

export class HttpJobDataSource implements JobDataSource {
  constructor(private readonly http: HttpClient) {}
  async list(companyId: string, search: JobSearchDto, signal: AbortSignal) {
    const query = new URLSearchParams({ size: "50" });
    if (search.beforeAt !== null) query.set("beforeAt", search.beforeAt);
    if (search.beforeId !== null) query.set("beforeId", search.beforeId);
    return jobPageDto.parse(
      await this.http.request({ path: `/api/v1/companies/${companyId}/jobs?${query}` }, signal),
    );
  }
  async get(companyId: string, id: string, signal: AbortSignal) {
    return jobDto.parse(
      await this.http.request({ path: `/api/v1/companies/${companyId}/jobs/${id}` }, signal),
    );
  }
  async requestCancellation(
    companyId: string,
    id: string,
    expectedVersion: number,
    signal: AbortSignal,
  ) {
    return jobDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${companyId}/jobs/${id}/cancel`,
          method: "POST",
          body: { expectedVersion },
        },
        signal,
      ),
    );
  }
}
