import type { CompanyId } from "../../../../core/domain/identifiers";
import type { JobId } from "../../../jobs/domain/entities/background-job";

export const workPeriodStatuses = ["OPEN", "PROCESSING", "REVIEW_REQUIRED", "CLOSED"] as const;
export type WorkPeriodStatus = (typeof workPeriodStatuses)[number];
export type WorkPeriodId = string & { readonly workPeriodId: unique symbol };
export type WorkPeriod = Readonly<{
  id: WorkPeriodId;
  companyId: CompanyId;
  month: string;
  status: WorkPeriodStatus;
  timezone: string | null;
  jobId: JobId | null;
  version: number;
  startedAt: string | null;
  closedAt: string | null;
  failureCode: string | null;
}>;
