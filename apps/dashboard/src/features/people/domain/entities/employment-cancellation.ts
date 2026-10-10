import type { EmployeeId } from "./employee";

export type EmploymentCancellation = Readonly<{
  employeeId: EmployeeId;
  expectedVersion: number;
  revision: number;
  reason: string;
}>;
