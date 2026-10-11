import type { ShiftSnapshot } from "./shift-snapshot";

export type ScheduledDay = Readonly<
  | {
      kind: "WORK";
      workDate: string;
      shift: ShiftSnapshot;
      startsAt: string;
      endsAt: string;
      plannedMinutes: number;
      origin: "PATTERN" | "ROSTER";
      originVersion: number;
    }
  | {
      kind: "OFF";
      workDate: string;
      origin: "PATTERN" | "ROSTER" | "HOLIDAY";
      originVersion: number;
      holidayId: string | null;
    }
  | { kind: "UNASSIGNED"; workDate: string }
>;
