import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeStatus } from "../../../workforce/domain/entities/overtime-request";

export type OvertimeReportCounts = Readonly<{
  requests: number;
  employments: number;
  actualRequests: number;
  requestedMinutes: number;
  actualMinutes: number;
  approvedMinutes: number;
}>;
export type OvertimeReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  status: OvertimeStatus | null;
  evaluatedAt: string;
  definitionVersion: "overtime.v1";
  totals: OvertimeReportCounts;
  months: readonly Readonly<{
    month: string;
    from: string;
    until: string;
    counts: OvertimeReportCounts;
  }>[];
}>;
