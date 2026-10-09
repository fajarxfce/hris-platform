import type { JobDto, JobPageDto } from "../models/job-dto";
import type { JobSearchDto } from "../models/job-search-dto";

export interface JobDataSource {
  list(companyId: string, search: JobSearchDto, signal: AbortSignal): Promise<JobPageDto>;
  get(companyId: string, id: string, signal: AbortSignal): Promise<JobDto>;
  requestCancellation(
    companyId: string,
    id: string,
    expectedVersion: number,
    signal: AbortSignal,
  ): Promise<JobDto>;
}
