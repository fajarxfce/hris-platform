import type { EmployeeId } from "./employee";
import type { AssignedEmploymentTerms } from "./employment-assignments";

export type EmploymentChange = Readonly<{
  employeeId: EmployeeId;
  expectedVersion: number;
  terms: AssignedEmploymentTerms;
  reason: string;
}>;
