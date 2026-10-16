import type { ShiftReference } from "./shift-reference";

export type RosterBulkMode = "SHIFT" | "DAY_OFF" | "CALENDAR";
export type RosterBulkSelection = Readonly<{
  employeeIds: readonly string[];
  dates: readonly string[];
  mode: RosterBulkMode;
  shift: ShiftReference | null;
}>;
