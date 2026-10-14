import type { PayrollPeriod } from "./payroll-period";
import type { PayrollRun } from "./payroll-run";
import type { PayrollRunItem } from "./payroll-run-item";
export type PayrollRunAttempt = Readonly<{
  jobId: string;
  number: number;
  baseCompleted: number;
  startedAt: string;
  reason: string;
}>;
export type PayrollRunJob = Readonly<{
  jobId: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  completedItems: number;
  totalItems: number;
  attempts: number;
  version: number;
  cancellationRequested: boolean;
  failureCode: string | null;
}>;
export type PayrollRunAction = "CANCEL_JOB" | "RESUME" | "ABANDON";
export type PayrollRunDetails = Readonly<{
  run: PayrollRun;
  period: PayrollPeriod;
  attempts: readonly PayrollRunAttempt[];
  job: PayrollRunJob;
  results: Readonly<{ items: readonly PayrollRunItem[]; nextCursor: string | null }>;
  availableActions: readonly PayrollRunAction[];
}>;
