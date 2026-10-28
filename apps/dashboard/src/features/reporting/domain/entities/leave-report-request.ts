import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveStatus } from "../../../leave/domain/entities/leave-request";

export type LeaveReportQuery = Readonly<{
  from: string;
  until: string;
  status: LeaveStatus | null;
  after: string | null;
}>;
export type LeaveReportRequest = Readonly<{
  id: string;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  typeCode: string;
  typeName: string;
  status: LeaveStatus;
  submittedAt: string;
  version: number;
  days: string;
  requestDays: string;
}>;
export type LeaveReportRequestPage = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  status: LeaveStatus | null;
  evaluatedAt: string;
  definitionVersion: "leave.v1";
  items: readonly LeaveReportRequest[];
  nextCursor: string | null;
}>;
