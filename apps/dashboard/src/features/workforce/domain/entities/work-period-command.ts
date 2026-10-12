import type { WorkPeriodId } from "./work-period";

export type WorkPeriodCommand = Readonly<{
  month: string;
  periodId: WorkPeriodId | null;
  expectedVersion: number;
  reason: string;
}>;
