import type { CompanyId } from "../../../../core/domain/identifiers";

export type JobId = string & { readonly jobId: unique symbol };
export type JobStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
export type BackgroundJob = Readonly<{
  id: JobId;
  companyId: CompanyId;
  kind: string;
  status: JobStatus;
  completedItems: number;
  totalItems: number;
  progressMode: "FIXED_TOTAL" | "UPPER_BOUND";
  attempts: number;
  cancellationRequested: boolean;
  failureCode: string | null;
  createdAt: string;
  finishedAt: string | null;
  version: number;
  availableActions: readonly string[];
  scheduledFor: string | null;
  availableAt: string;
}>;
