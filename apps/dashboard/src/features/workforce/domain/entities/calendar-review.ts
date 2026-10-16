import type { CalendarRange } from "./calendar-range";
import type { RosterOverride } from "./roster-override";
import type { ScheduleAssignment } from "./schedule-assignment";
import type { ScheduledDay } from "./scheduled-day";
import type { WorkforceEmployee } from "./workforce-employee";

export type CalendarReview = Readonly<{
  employee: WorkforceEmployee;
  range: CalendarRange;
  scheduleVersion: number | null;
  days: readonly ScheduledDay[];
  assignmentAtStart: ScheduleAssignment | null;
  rosterOverrides: readonly RosterOverride[];
  supportsRosterReset: boolean;
}>;
