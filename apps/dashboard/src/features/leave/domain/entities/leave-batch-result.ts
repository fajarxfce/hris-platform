import type { LeaveEmployeeReference } from "./leave-employee-reference";
export type LeaveBatchResult = Readonly<{
  ordinal: number;
  employee: LeaveEmployeeReference;
  jobId: string;
  status: "APPLIED" | "UNCHANGED" | "SKIPPED" | "FAILED";
  resourceId: string | null;
  failureCode: string | null;
  parameters: Readonly<Record<string, string>>;
  completedAt: string;
}>;
