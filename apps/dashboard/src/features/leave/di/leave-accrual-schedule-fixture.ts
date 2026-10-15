import {
  toLeaveAccrualScheduleDetails,
  toLeaveAccrualScheduleReview,
} from "../data/mappers/leave-accrual-schedule-mapper";
import type {
  LeaveAccrualScheduleDetailsDto,
  LeaveAccrualScheduleReviewDto,
} from "../data/models/leave-accrual-schedule-dto";
import type { LeaveAccrualScheduleId } from "../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleCreation } from "../domain/entities/leave-accrual-schedule-command";
import {
  batchAccount,
  batchCompany,
  batchReviewWire,
  batchType,
  batchWire,
} from "./leave-batch-fixture";
export const accrualScheduleId = "a0000000-abcd-4000-8000-000000000001" as LeaveAccrualScheduleId;
export const accrualSelection = { typeId: batchType, firstPeriod: "2026-10", periods: 3 };
export const accrualMonths = ["2026-10", "2026-11", "2026-12"];
export const accrualDue = ["2026-10-31T17:00:00Z", "2026-11-30T17:00:00Z", "2026-12-31T17:00:00Z"];
export const accrualReviewWire: LeaveAccrualScheduleReviewDto = {
  companyDate: "2026-10-15",
  timezone: "Asia/Jakarta",
  frequency: "MONTHLY",
  policyVersion: 4,
  employeeIds: [...batchReviewWire.employeeIds],
  periods: accrualMonths.map((period, index) => ({
    period,
    dueAt: accrualDue[index] ?? "",
    type: structuredClone(batchReviewWire.type),
  })),
};
export const accrualWire: LeaveAccrualScheduleDetailsDto = {
  schedule: {
    id: accrualScheduleId,
    typeId: batchType,
    typeCode: "ANNUAL",
    typeName: "Annual leave",
    policyVersion: 4,
    frequency: "MONTHLY",
    firstPeriod: "2026-10",
    periodCount: 3,
    totalEmployees: 3,
    timezone: "Asia/Jakarta",
    actorId: batchAccount,
    createdAt: "2026-10-15T01:00:00Z",
    reason: "Reviewed entitlement schedule",
  },
  periods: accrualMonths.map((period, index) => ({
    scheduledFor: accrualDue[index] ?? "",
    jobStatus: "QUEUED",
    completedEmployees: 0,
    batch: {
      ...structuredClone(batchWire.batch),
      id: `40000000-abcd-4000-8000-00000000000${index + 1}`,
      jobId: `60000000-0000-4000-8000-00000000000${index + 1}`,
      period,
      reason: "Reviewed entitlement schedule",
    },
  })),
};
export const accrualReview = toLeaveAccrualScheduleReview(
  accrualReviewWire,
  batchCompany,
  accrualSelection,
);
export const accrualDetails = toLeaveAccrualScheduleDetails(
  accrualWire,
  batchCompany,
  accrualScheduleId,
);
export const accrualCreation = (): LeaveAccrualScheduleCreation => ({
  ...accrualSelection,
  id: accrualScheduleId,
  companyId: batchCompany,
  expectedPolicyVersion: 4,
  employeeIds: [...accrualReview.employeeIds],
  reason: "  Reviewed entitlement schedule  ",
});
