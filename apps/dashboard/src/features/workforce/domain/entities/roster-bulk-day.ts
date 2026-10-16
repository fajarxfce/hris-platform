import type { ScheduledDay } from "./scheduled-day";

export type RosterBulkDay = Readonly<{
  workDate: string;
  expectedVersion: number | null;
  overrideActive: boolean;
  before: ScheduledDay;
  after: ScheduledDay;
  changed: boolean;
}>;
