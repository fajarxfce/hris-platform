import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceDay } from "./attendance-day";
import type { CalendarRange } from "./calendar-range";

export type EmployeeAttendance = Readonly<{
  companyId: CompanyId;
  employeeId: EmployeeId;
  range: CalendarRange;
  days: readonly AttendanceDay[];
}>;
