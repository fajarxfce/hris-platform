import type { CompanyId } from "../../../../core/domain/identifiers";

export const leaveCalendarScopes = ["COMPANY", "TEAM"] as const;
export type LeaveCalendarScope = (typeof leaveCalendarScopes)[number];
export type LeaveCalendarDay = Readonly<{
  date: string;
  employeeCount: number;
  requestCount: number;
  pendingCount: number;
  approvedCount: number;
  cancellationPendingCount: number;
}>;
export type LeaveCalendar = Readonly<{
  companyId: CompanyId;
  scope: LeaveCalendarScope;
  month: string;
  companyDate: string;
  timezone: string;
  days: readonly LeaveCalendarDay[];
}>;
