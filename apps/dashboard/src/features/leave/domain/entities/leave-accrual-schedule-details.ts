import type { LeaveAccrualSchedule } from "./leave-accrual-schedule";
import type { LeaveBatch } from "./leave-batch";
export type LeaveAccrualSchedulePeriod = Readonly<{
  scheduledFor: string;
  batch: LeaveBatch;
  jobStatus: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  completedEmployees: number;
}>;
export type LeaveAccrualScheduleDetails = Readonly<{
  schedule: LeaveAccrualSchedule;
  periods: readonly LeaveAccrualSchedulePeriod[];
}>;
