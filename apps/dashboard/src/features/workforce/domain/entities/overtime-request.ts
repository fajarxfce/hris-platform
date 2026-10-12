import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OvertimeInterval } from "./overtime-interval";
import type { ScheduledDay } from "./scheduled-day";

export type OvertimeRequestId = string & { readonly overtimeRequestId: unique symbol };
export const overtimeStatuses = [
  "PLANNED",
  "PENDING",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
] as const;
export type OvertimeStatus = (typeof overtimeStatuses)[number];

export type OvertimeRequest = Readonly<{
  id: OvertimeRequestId;
  companyId: CompanyId;
  employeeId: EmployeeId;
  employeeNumber: string;
  employeeName: string;
  requesterAccountId: AccountId | null;
  authorId: AccountId;
  createdAt: string;
  workDate: string;
  timezone: string;
  schedule: ScheduledDay;
  requested: OvertimeInterval;
  reason: string;
  status: OvertimeStatus;
  actual: OvertimeInterval | null;
  submittedBy: AccountId | null;
  submittedAt: string | null;
  approvalId: string | null;
  approvedMinutes: number;
  decidedAt: string | null;
  version: number;
}>;
