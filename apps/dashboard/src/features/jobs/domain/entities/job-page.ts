import type { CompanyId } from "../../../../core/domain/identifiers";
import type { BackgroundJob, JobId } from "./background-job";

export type JobCursor = Readonly<{ beforeAt: string; beforeId: JobId }>;
export type JobPage = Readonly<{
  companyId: CompanyId;
  items: readonly BackgroundJob[];
  next: JobCursor | null;
}>;
