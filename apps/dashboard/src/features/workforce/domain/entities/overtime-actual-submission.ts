import type { OvertimeActualTime } from "./overtime-actual-time";
import type { OvertimeRequestChange } from "./overtime-request-change";

export type OvertimeActualSubmission = OvertimeRequestChange &
  Readonly<{ actual: OvertimeActualTime }>;
