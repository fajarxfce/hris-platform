import type { AttendanceAdjustmentSummary } from "./attendance-adjustment-summary";

export type AttendanceAdjustmentPage = Readonly<{
  items: readonly AttendanceAdjustmentSummary[];
  nextCursor: string | null;
}>;
