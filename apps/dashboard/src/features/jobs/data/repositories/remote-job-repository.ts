import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { JobId } from "../../domain/entities/background-job";
import type { CancelJobCommand } from "../../domain/entities/cancel-job-command";
import type { JobSearch } from "../../domain/entities/job-search";
import type { JobRepository } from "../../domain/repositories/job-repository";
import type { JobDataSource } from "../datasources/job-data-source";
import { toBackgroundJob } from "../mappers/job-mapper";
import { toJobPage } from "../mappers/job-page-mapper";

export class RemoteJobRepository implements JobRepository {
  constructor(private readonly source: JobDataSource) {}
  list(companyId: CompanyId, search: JobSearch, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toJobPage(await this.source.list(companyId, search, signal), companyId, search),
    );
  }
  get(companyId: CompanyId, id: JobId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toBackgroundJob(await this.source.get(companyId, id, signal), companyId, id),
    );
  }
  requestCancellation(companyId: CompanyId, command: CancelJobCommand, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const job = toBackgroundJob(
        await this.source.requestCancellation(
          companyId,
          command.jobId,
          command.expectedVersion,
          signal,
        ),
        companyId,
        command.jobId,
      );
      if (!job.cancellationRequested || job.version <= command.expectedVersion)
        throw new InvalidHttpResponseError();
      return job;
    });
  }
}
