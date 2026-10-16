import type { CompanyId } from "../../../../core/domain/identifiers";

export type AttendanceEvidenceDecision = Readonly<{
  eventId: string;
  expectedVersion: number;
  decision: "ACCEPT" | "REJECT";
}>;
export type AttendanceAdjustmentChange = Readonly<{
  companyId: CompanyId;
  employeeId: string;
  workDate: string;
  reviewRevision: string;
  expectedAdjustmentRevision: number;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number;
  decisions: readonly AttendanceEvidenceDecision[];
  reason: string;
}>;
