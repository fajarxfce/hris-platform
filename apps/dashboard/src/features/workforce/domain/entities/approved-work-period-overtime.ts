import type { OvertimeInterval } from "./overtime-interval";
import type { OvertimeRequestId } from "./overtime-request";
import type { ScheduledDay } from "./scheduled-day";

export type ApprovedWorkPeriodOvertime = Readonly<{
  requestId: OvertimeRequestId;
  revision: number;
  actual: OvertimeInterval;
  approvedMinutes: number;
  schedule: ScheduledDay;
}>;
