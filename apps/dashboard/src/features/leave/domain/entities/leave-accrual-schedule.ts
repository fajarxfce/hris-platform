import type { CompanyId } from "../../../../core/domain/identifiers";
export type LeaveAccrualScheduleId = string & { readonly __leaveAccrualScheduleId: unique symbol };
export type LeaveAccrualScheduleFrequency = "MONTHLY" | "ANNUAL";
export type LeaveAccrualSchedule = Readonly<{
  id: LeaveAccrualScheduleId;
  companyId: CompanyId;
  typeId: string;
  typeCode: string;
  typeName: string;
  policyVersion: number;
  frequency: LeaveAccrualScheduleFrequency;
  firstPeriod: string;
  periodCount: number;
  totalEmployees: number;
  timezone: string;
  actorId: string;
  createdAt: string;
  reason: string;
}>;
export type LeaveAccrualSchedulePage = Readonly<{
  items: readonly LeaveAccrualSchedule[];
  nextCursor: string | null;
}>;
