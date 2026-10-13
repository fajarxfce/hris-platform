import type { LeaveDay } from "./leave-day";

export type RequestedLeaveDay = Readonly<Pick<LeaveDay, "workDate" | "portion">>;
export type LeaveRequestSelection = Readonly<{
  employeeId: string;
  typeId: string;
  days: readonly RequestedLeaveDay[];
}>;
