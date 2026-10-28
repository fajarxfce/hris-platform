import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceExceptionCounts } from "./attendance-exception-counts";

export type AttendanceExceptionReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  definitionVersion: "attendance-exceptions.v1";
  totals: AttendanceExceptionCounts;
  days: readonly Readonly<{ date: string; counts: AttendanceExceptionCounts }>[];
}>;
