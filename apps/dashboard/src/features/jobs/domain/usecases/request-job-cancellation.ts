import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { BackgroundJob } from "../entities/background-job";
import { canRequestJobCancellation } from "../policies/job-policy";
import type { JobRepository } from "../repositories/job-repository";

export class RequestJobCancellation {
  constructor(private readonly jobs: JobRepository) {}
  execute(
    access: CompanyAccess,
    observed: BackgroundJob,
    signal: AbortSignal,
  ): Promise<Result<BackgroundJob>> {
    signal.throwIfAborted();
    if (observed.companyId !== access.companyId || !canRequestJobCancellation(observed))
      return Promise.resolve(failed("access_denied"));
    return this.jobs.requestCancellation(
      access.companyId,
      Object.freeze({ jobId: observed.id, expectedVersion: observed.version }),
      signal,
    );
  }
}
