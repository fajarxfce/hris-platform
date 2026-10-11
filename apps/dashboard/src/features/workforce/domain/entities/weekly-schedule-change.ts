import type { Weekday } from "./schedule-assignment";
import type { ShiftReference } from "./shift-reference";

export type WeeklyScheduleChange = Readonly<{
  employeeId: string;
  effectiveFrom: string;
  days: Readonly<Partial<Record<Weekday, ShiftReference>>>;
  expectedVersion: number | null;
  reason: string;
}>;
