import type { EmployeeAttendance } from "./employee-attendance";
import type { WorkforceEmployee } from "./workforce-employee";

export type EmployeeAttendanceReview = EmployeeAttendance &
  Readonly<{ employee: WorkforceEmployee }>;
