import type { Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { JobPage } from "../entities/job-page";
import type { JobSearch } from "../entities/job-search";
import { validateJobSearch } from "../policies/job-policy";
import type { JobRepository } from "../repositories/job-repository";

/** Members retain access to their own jobs; company-wide visibility is authorized by the server. */
export class LoadJobs {
  constructor(private readonly jobs: JobRepository) {}
  execute(access: CompanyAccess, search: JobSearch, signal: AbortSignal): Promise<Result<JobPage>> {
    signal.throwIfAborted();
    const failure = validateJobSearch(search);
    if (failure) return Promise.resolve({ ok: false, failure });
    return this.jobs.list(
      access.companyId,
      Object.freeze({ ...search, beforeId: search.beforeId?.toLowerCase() ?? null }),
      signal,
    );
  }
}
