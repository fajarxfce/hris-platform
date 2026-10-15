import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveCalendarScope } from "./leave-calendar";
import type { LeaveDay } from "./leave-day";
import type { LeaveRequestId } from "./leave-request";

export type LeaveCalendarEntry = Readonly<{
  requestId: LeaveRequestId;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  typeCode: string;
  typeName: string;
  portion: LeaveDay["portion"];
  status: "PENDING" | "APPROVED" | "CANCELLATION_PENDING";
  version: number;
}>;
export type LeaveCalendarEntries = Readonly<{
  companyId: CompanyId;
  scope: LeaveCalendarScope;
  date: string;
  companyDate: string;
  timezone: string;
  entries: Readonly<{
    items: readonly LeaveCalendarEntry[];
    nextCursor: string | null;
  }>;
}>;
