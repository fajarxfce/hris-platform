import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { BackgroundJob, JobId } from "../entities/background-job";
import { isJobId } from "../policies/job-policy";
import type { JobRepository } from "../repositories/job-repository";

export class LoadJob {
  constructor(private readonly jobs: JobRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal): Promise<Result<BackgroundJob>> {
    signal.throwIfAborted();
    if (!isJobId(id)) return Promise.resolve(failed("job_not_found"));
    return this.jobs.get(access.companyId, id.toLowerCase() as JobId, signal);
  }
}
