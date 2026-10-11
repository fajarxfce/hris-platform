import type { AttendanceCorrection } from "./attendance-correction";
import type { AttendanceEntry } from "./attendance-entry";

export type AttendanceDay = Readonly<{
  workDate: string;
  entries: readonly AttendanceEntry[];
  acceptedMinutes: number;
  pendingCount: number;
  incomplete: boolean;
  correction: AttendanceCorrection | null;
}>;
