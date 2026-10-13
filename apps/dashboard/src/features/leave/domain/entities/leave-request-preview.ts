import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveDay } from "./leave-day";
import type { LeavePolicy } from "./leave-policy";
import type { LeaveRequestSelection } from "./leave-request-selection";

export type LeavePreviewBalance = Readonly<{
  year: number;
  availableDays: string;
  reservedDays: string;
  consumedDays: string;
  requestedDays: string;
  remainingDays: string;
  version: number;
}>;
export type LeaveRequestPreview = Readonly<{
  companyId: CompanyId;
  employeeId: string;
  evaluatedAt: string;
  timezone: string;
  policy: LeavePolicy;
  days: readonly LeaveDay[];
  chargedDays: string;
  excludedDates: readonly string[];
  balances: readonly LeavePreviewBalance[];
  revision: string;
}>;
export type ReviewedLeaveSelection = Readonly<{
  selection: LeaveRequestSelection;
  preview: LeaveRequestPreview;
}>;
