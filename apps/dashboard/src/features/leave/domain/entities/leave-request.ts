import type { CompanyId } from "../../../../core/domain/identifiers";

export type LeaveRequestId = string & { readonly leaveRequestId: unique symbol };
export const leaveStatuses = [
  "PENDING",
  "APPROVED",
  "REJECTED",
  "CANCELLED",
  "CANCELLATION_PENDING",
] as const;
export type LeaveStatus = (typeof leaveStatuses)[number];
export type LeaveRequestSummary = Readonly<{
  id: LeaveRequestId;
  companyId: CompanyId;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  typeCode: string;
  typeName: string;
  from: string;
  until: string;
  chargedDays: string;
  status: LeaveStatus;
  submittedAt: string;
  version: number;
}>;
export type LeaveRequestPage = Readonly<{
  items: readonly LeaveRequestSummary[];
  nextCursor: string | null;
}>;
export type LeaveRequestQuery = Readonly<{
  employeeId: string | null;
  status: LeaveStatus | null;
  after: string | null;
}>;
