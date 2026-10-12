import type { EmployeeId } from "../../../people/domain/entities/employee";

export type AttendanceException = Readonly<{
  employeeId: EmployeeId;
  employeeNumber: string;
  employeeName: string;
  workDate: string;
  pendingCount: number;
  incomplete: boolean;
  correctionVersion: number | null;
  lastReceivedAt: string;
}>;
