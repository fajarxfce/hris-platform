import type { ClosedWorkDay } from "./closed-work-day";
import type { WorkPeriod } from "./work-period";
import type { WorkforceEmployee } from "./workforce-employee";

export type WorkPeriodSnapshot = Readonly<{
  employee: WorkforceEmployee;
  period: WorkPeriod;
  days: readonly ClosedWorkDay[];
}>;
