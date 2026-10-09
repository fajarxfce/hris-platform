import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { BackgroundJob, JobId } from "../entities/background-job";
import type { CancelJobCommand } from "../entities/cancel-job-command";
import type { JobPage } from "../entities/job-page";
import type { JobSearch } from "../entities/job-search";

export interface JobRepository {
  list(companyId: CompanyId, search: JobSearch, signal: AbortSignal): Promise<Result<JobPage>>;
  get(companyId: CompanyId, id: JobId, signal: AbortSignal): Promise<Result<BackgroundJob>>;
  requestCancellation(
    companyId: CompanyId,
    command: CancelJobCommand,
    signal: AbortSignal,
  ): Promise<Result<BackgroundJob>>;
}
