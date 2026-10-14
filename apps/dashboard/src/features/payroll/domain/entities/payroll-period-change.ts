import type { PayrollPeriodStatus } from "./payroll-period";
export type PayrollPeriodChange = Readonly<{
  version: number;
  status: PayrollPeriodStatus;
  actorId: string;
  recordedAt: string;
  reason: string;
  runId: string | null;
}>;
