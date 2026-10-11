import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "./employee";
import type { AssignedEmploymentTerms } from "./employment-assignments";

export type EmploymentTransferInput = Readonly<{
  employeeId: EmployeeId;
  targetCompanyId: CompanyId;
  targetEmploymentId: EmployeeId;
  expectedVersion: number;
  employeeNumber: string;
  terms: AssignedEmploymentTerms;
  reason: string;
}>;
