import type { EmployeeId } from "./employee";
import type { EmploymentRevision } from "./employment-revision";

export type EmploymentRevisionDetails = Readonly<{
  employeeId: EmployeeId;
  version: number;
  companyDate: string;
  revision: EmploymentRevision;
  canCancel: boolean;
}>;
