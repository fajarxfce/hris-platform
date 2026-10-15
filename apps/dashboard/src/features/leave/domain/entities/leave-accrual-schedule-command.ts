import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveAccrualScheduleId } from "./leave-accrual-schedule";
import type { LeaveAccrualScheduleSelection } from "./leave-accrual-schedule-review";
export type LeaveAccrualScheduleCreation = Readonly<
  LeaveAccrualScheduleSelection & {
    id: LeaveAccrualScheduleId;
    companyId: CompanyId;
    expectedPolicyVersion: number;
    employeeIds: readonly string[];
    reason: string;
  }
>;
