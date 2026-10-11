import type { ShiftReference } from "./shift-reference";

export type RosterDayChange = Readonly<{
  employeeId: string;
  workDate: string;
  shift: ShiftReference | null;
  expectedVersion: number | null;
  reason: string;
}>;
