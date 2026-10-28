import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveStatus } from "../../../leave/domain/entities/leave-request";

export type LeaveReportCounts = Readonly<{ requests: number; employments: number; days: string }>;

export type LeaveReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  definitionVersion: "leave.v1";
  totals: LeaveReportCounts;
  statuses: readonly Readonly<{ status: LeaveStatus; counts: LeaveReportCounts }>[];
}>;
