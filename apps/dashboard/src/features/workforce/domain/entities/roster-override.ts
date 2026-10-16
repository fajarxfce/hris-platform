import type { ShiftSnapshot } from "./shift-snapshot";

export type RosterOverride = Readonly<{
  workDate: string;
  version: number;
  active: boolean;
  shift: ShiftSnapshot | null;
}>;
