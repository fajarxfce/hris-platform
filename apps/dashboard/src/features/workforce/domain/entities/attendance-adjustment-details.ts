import type { AttendanceAdjustmentSummary } from "./attendance-adjustment-summary";
import type { WorkPeriodSnapshot } from "./work-period-snapshot";

export type AttendanceAdjustmentDetails = Readonly<{
  summary: AttendanceAdjustmentSummary;
  snapshot: WorkPeriodSnapshot;
}>;
