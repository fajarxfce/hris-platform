import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpJobDataSource } from "../data/datasources/http-job-data-source";
import { RemoteJobRepository } from "../data/repositories/remote-job-repository";
import { LoadJob } from "../domain/usecases/load-job";
import { LoadJobs } from "../domain/usecases/load-jobs";
import { RequestJobCancellation } from "../domain/usecases/request-job-cancellation";

export function createJobsFeature(http: HttpClient) {
  const jobs = new RemoteJobRepository(new HttpJobDataSource(http));
  return {
    loadJobs: new LoadJobs(jobs),
    loadJob: new LoadJob(jobs),
    requestCancellation: new RequestJobCancellation(jobs),
  };
}
