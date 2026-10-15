import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveAccrualScheduleFrequency } from "./leave-accrual-schedule";
import type { LeavePolicyDefinition } from "./leave-policy-definition";
export type LeaveAccrualScheduleSelection = Readonly<{
  typeId: string;
  firstPeriod: string;
  periods: number;
}>;
export type LeaveAccrualPeriodReview = Readonly<{
  period: string;
  dueAt: string;
  type: Readonly<LeavePolicyDefinition & { appliedRevision: number }>;
}>;
export type LeaveAccrualScheduleReview = Readonly<{
  companyId: CompanyId;
  selection: LeaveAccrualScheduleSelection;
  companyDate: string;
  timezone: string;
  frequency: LeaveAccrualScheduleFrequency;
  policyVersion: number;
  periods: readonly LeaveAccrualPeriodReview[];
  employeeIds: readonly string[];
}>;
