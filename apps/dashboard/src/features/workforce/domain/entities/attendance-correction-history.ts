import type { AttendanceCorrectionPage } from "./attendance-correction-page";
import type { WorkforceEmployee } from "./workforce-employee";

export type AttendanceCorrectionHistory = AttendanceCorrectionPage &
  Readonly<{ employee: WorkforceEmployee }>;
