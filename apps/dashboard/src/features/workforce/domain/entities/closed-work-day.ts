import type { ApprovedWorkPeriodOvertime } from "./approved-work-period-overtime";
import type { ScheduledDay } from "./scheduled-day";

export const workDayFacts = [
  "WORKED",
  "ABSENCE_RECORDED",
  "UNRECORDED",
  "OFF",
  "UNASSIGNED",
] as const;
export type WorkDayFact = (typeof workDayFacts)[number];
export type ClosedWorkDay = Readonly<{
  workDate: string;
  fact: WorkDayFact;
  acceptedMinutes: number;
  schedule: ScheduledDay;
  evidenceIds: readonly string[];
  correctionId: string | null;
  overtime: readonly ApprovedWorkPeriodOvertime[];
}>;
