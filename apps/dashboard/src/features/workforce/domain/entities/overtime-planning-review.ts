import type { ScheduledDay } from "./scheduled-day";
import type { WorkforceEmployee } from "./workforce-employee";

export type OvertimePlanningReview = Readonly<{
  employee: WorkforceEmployee;
  workDate: string;
  companyDate: string;
  timezone: string;
  schedule: Exclude<ScheduledDay, { kind: "UNASSIGNED" }>;
  revision: string;
}>;
