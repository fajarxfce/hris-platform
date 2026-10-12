import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceCorrection } from "./attendance-correction";

export type AttendanceCorrectionPage = Readonly<{
  companyId: CompanyId;
  employeeId: EmployeeId;
  workDate: string;
  items: readonly AttendanceCorrection[];
  nextCursor: string | null;
}>;
