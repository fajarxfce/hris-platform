import type { RosterBulkDay } from "./roster-bulk-day";
import type { WorkforceEmployee } from "./workforce-employee";

export type RosterBulkEmployee = Readonly<{
  employee: WorkforceEmployee;
  days: readonly RosterBulkDay[];
}>;
